package com.btc.expenseservice.service.impl;

import com.btc.expenseservice.audit.AuditService;
import com.btc.expenseservice.dto.TravelPolicyRequestDto;
import com.btc.expenseservice.dto.TravelPolicyResponseDto;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.entity.TravelPolicy;
import com.btc.expenseservice.exception.ExpenseConflictException;
import com.btc.expenseservice.exception.InvalidRequestException;
import com.btc.expenseservice.exception.PolicyNotFoundException;
import com.btc.expenseservice.exception.PolicyViolationException;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.repository.TravelPolicyRepository;
import com.btc.expenseservice.security.CurrentUser;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company travel policy. Everyone may read policies; only administrators create or change them. Periods never
 * overlap, so exactly zero or one policy applies to an expense date. Violations reject the expense (HTTP 422);
 * amounts are never changed, and expenses saved before a policy existed are not re-validated.
 */
@Service
@Transactional
public class TravelPolicyService {

    private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    private final TravelPolicyRepository policyRepository;
    private final ExpenseRepository expenseRepository;
    private final String currency;
    private final AuditService auditService;

    public TravelPolicyService(TravelPolicyRepository policyRepository, ExpenseRepository expenseRepository,
                               @Value("${btc.expenses.currency:USD}") String currency, AuditService auditService) {
        this.auditService = auditService;
        this.policyRepository = policyRepository;
        this.expenseRepository = expenseRepository;
        this.currency = currency;
    }

    @Transactional(readOnly = true)
    public List<TravelPolicyResponseDto> list() {
        LocalDate today = LocalDate.now();
        return policyRepository.findAllByOrderByEffectiveFromDesc().stream().map(policy -> toResponse(policy, today)).toList();
    }

    @Transactional(readOnly = true)
    public Optional<TravelPolicyResponseDto> applicableOn(LocalDate date) {
        return applicable(date).map(policy -> toResponse(policy, LocalDate.now()));
    }

    public TravelPolicyResponseDto create(TravelPolicyRequestDto request, CurrentUser actor) {
        actor.requireAdmin();
        validate(request, -1L);
        LocalDateTime now = LocalDateTime.now();
        TravelPolicy policy = TravelPolicy.builder().createdBy(actor.id()).createdAt(now).build();
        apply(policy, request, actor, now);
        TravelPolicy saved = policyRepository.save(policy);
        auditService.record(actor.id(), "TRAVEL_POLICY_CREATED", "TRAVEL_POLICY", saved.getId(), describe(saved));
        return toResponse(saved, LocalDate.now());
    }

    public TravelPolicyResponseDto update(Long id, TravelPolicyRequestDto request, CurrentUser actor) {
        actor.requireAdmin();
        TravelPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Travel policy not found with id: " + id));
        validate(request, id);
        String before = describe(policy);
        apply(policy, request, actor, LocalDateTime.now());
        TravelPolicy saved = policyRepository.saveAndFlush(policy);
        auditService.record(actor.id(), "TRAVEL_POLICY_UPDATED", "TRAVEL_POLICY", id, "Before: " + before + ". After: " + describe(saved));
        return toResponse(saved, LocalDate.now());
    }

    private static String describe(TravelPolicy policy) {
        return "“%s” %s – %s, trip limit %s, category limits %s".formatted(policy.getName(), policy.getEffectiveFrom(),
                policy.getEffectiveTo() == null ? "open-ended" : policy.getEffectiveTo(),
                policy.getTripLimit() == null ? "none" : policy.getTripLimit().toPlainString(), new TreeMap<>(policy.getCategoryLimits()));
    }

    @Transactional(readOnly = true)
    public TravelPolicy require(Long id) {
        return policyRepository.findById(id).orElseThrow(() -> new PolicyNotFoundException("Travel policy not found with id: " + id));
    }

    /**
     * Checks a new or changed expense against the policy for its date. Runs inside the expense transaction; the
     * trip check locks the trip's expenses so concurrent expenses cannot jointly exceed the trip limit.
     *
     * @param excludeExpenseId the expense being updated (its old amount is replaced), or null for a new one
     */
    public void enforce(String category, BigDecimal amount, LocalDate date, Long tripId, Long excludeExpenseId) {
        Optional<TravelPolicy> found = applicable(date);
        if (found.isEmpty()) {
            return;
        }
        TravelPolicy policy = found.get();
        BigDecimal categoryLimit = policy.getCategoryLimits().get(category);
        if (categoryLimit != null && amount.compareTo(categoryLimit) > 0) {
            throw new PolicyViolationException("This %s expense of %s exceeds the travel policy “%s” limit of %s per expense."
                    .formatted(category.toLowerCase(Locale.ROOT), money(amount), policy.getName(), money(categoryLimit)));
        }
        if (tripId != null && policy.getTripLimit() != null) {
            BigDecimal others = expenseRepository.lockTripExpenses(tripId).stream()
                    .filter(expense -> !Objects.equals(expense.getId(), excludeExpenseId))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal total = others.add(amount);
            if (total.compareTo(policy.getTripLimit()) > 0) {
                throw new PolicyViolationException(("This expense would bring the trip's spending to %s, above the travel "
                        + "policy “%s” trip limit of %s.").formatted(money(total), policy.getName(), money(policy.getTripLimit())));
            }
        }
    }

    private Optional<TravelPolicy> applicable(LocalDate date) {
        return policyRepository.findApplicable(date).stream().findFirst();
    }

    private void validate(TravelPolicyRequestDto request, Long excludeId) {
        if (!currency.equals(request.currency())) {
            throw new InvalidRequestException("Policies must use the application currency " + currency
                    + " (expenses are recorded in " + currency + ")");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new InvalidRequestException("'Effective to' must not be before 'effective from'");
        }
        LocalDate end = request.effectiveTo() == null ? OPEN_END : request.effectiveTo();
        if (policyRepository.countOverlapping(request.effectiveFrom(), end, excludeId) > 0) {
            throw new ExpenseConflictException("Another travel policy already covers part of this period; "
                    + "end it first (set its 'effective to') or choose different dates");
        }
    }

    private static void apply(TravelPolicy policy, TravelPolicyRequestDto request, CurrentUser actor, LocalDateTime now) {
        policy.setName(request.name().trim());
        policy.setCurrency(request.currency());
        policy.setTripLimit(request.tripLimit());
        policy.setEffectiveFrom(request.effectiveFrom());
        policy.setEffectiveTo(request.effectiveTo());
        policy.getCategoryLimits().clear();
        if (request.categoryLimits() != null) {
            policy.getCategoryLimits().putAll(new TreeMap<>(request.categoryLimits()));
        }
        policy.setUpdatedBy(actor.id());
        policy.setUpdatedAt(now);
    }

    private String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setCurrency(Currency.getInstance(currency));
        return format.format(amount);
    }

    static TravelPolicyResponseDto toResponse(TravelPolicy policy, LocalDate today) {
        boolean active = !policy.getEffectiveFrom().isAfter(today)
                && (policy.getEffectiveTo() == null || !policy.getEffectiveTo().isBefore(today));
        return new TravelPolicyResponseDto(policy.getId(), policy.getName(), policy.getCurrency(), policy.getTripLimit(),
                policy.getEffectiveFrom(), policy.getEffectiveTo(), Map.copyOf(policy.getCategoryLimits()),
                policy.getUpdatedAt(), active);
    }
}
