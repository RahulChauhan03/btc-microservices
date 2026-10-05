package com.btc.expenseservice.service.impl;

import com.btc.expenseservice.client.TripClient;
import com.btc.expenseservice.dto.CategoryTotalDto;
import com.btc.expenseservice.dto.ExpenseListFilter;
import com.btc.expenseservice.dto.ExpenseRequestDto;
import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.dto.ExpenseSummaryDto;
import com.btc.expenseservice.dto.MonthTotalDto;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.exception.ExpenseConflictException;
import com.btc.expenseservice.exception.ExpenseNotFoundException;
import com.btc.expenseservice.exception.InvalidExpenseException;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.security.CurrentUser;
import com.btc.expenseservice.service.ExpenseService;
import com.btc.expenseservice.web.DateRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ownership rules: users see and change only their own expenses. Administrators can read every expense
 * but can modify only their own. An expense may reference a trip only if the caller owns that trip.
 * An expense locked by a claim (see ExpenseLockService) cannot be edited or deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final TravelPolicyService travelPolicyService;
    private final TripClient tripClient;

    @Override
    public ExpenseResponseDto createExpense(ExpenseRequestDto requestDto, CurrentUser actor) {
        requireOwnTrip(requestDto.getTripId(), actor);
        travelPolicyService.enforce(requestDto.getCategory(), requestDto.getAmount(), requestDto.getExpenseDate(),
                requestDto.getTripId(), null);

        Expense expense = Expense.builder()
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .amount(requestDto.getAmount())
                .category(requestDto.getCategory())
                .expenseDate(requestDto.getExpenseDate())
                .tripId(requestDto.getTripId())
                .ownerId(actor.id())
                .build();

        return ExpenseMapper.toResponse(expenseRepository.save(expense));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponseDto getExpenseById(Long id, CurrentUser actor) {
        Expense expense = findExpenseById(id);
        if (!actor.admin() && !actor.owns(expense.getOwnerId())) {
            throw notFound(id);
        }
        return ExpenseMapper.toResponse(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseResponseDto> getAllExpenses(CurrentUser actor, ExpenseListFilter filter, Pageable pageable) {
        Long ownerId = actor.scopeOwner(filter.ownerId());
        Specification<Expense> spec = (root, query, cb) -> cb.conjunction();
        if (ownerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ownerId"), ownerId));
        }
        if (filter.tripId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("tripId"), filter.tripId()));
        }
        if (filter.category() != null && !filter.category().isBlank()) {
            String category = filter.category().trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category));
        }
        if (filter.from() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("expenseDate"), filter.from()));
        }
        if (filter.to() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("expenseDate"), filter.to()));
        }
        return expenseRepository.findAll(spec, pageable).map(ExpenseMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseSummaryDto getSummary(CurrentUser actor, Long requestedOwnerId, Long tripId, LocalDate from,
                                        LocalDate to) {
        Long ownerId = actor.scopeOwner(requestedOwnerId);
        // A single trip's expenses are naturally bounded, so its totals cover all of them unless a range is given.
        DateRange range = tripId != null && from == null && to == null ? DateRange.ALL_TIME : DateRange.resolve(from, to);
        List<CategoryTotalDto> byCategory = expenseRepository.totalsByCategory(ownerId, tripId, range.from(), range.to());
        List<MonthTotalDto> byMonth = expenseRepository.totalsByMonth(ownerId, tripId, range.from(), range.to());
        long count = byCategory.stream().mapToLong(CategoryTotalDto::count).sum();
        BigDecimal total = byCategory.stream().map(CategoryTotalDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ExpenseSummaryDto(range.from(), range.to(), count, total, byCategory, byMonth);
    }

    @Override
    public ExpenseResponseDto updateExpense(Long id, ExpenseRequestDto requestDto, CurrentUser actor) {
        Expense existingExpense = findOwnedExpense(id, actor);
        requireUnlocked(existingExpense);
        requireOwnTrip(requestDto.getTripId(), actor);
        travelPolicyService.enforce(requestDto.getCategory(), requestDto.getAmount(), requestDto.getExpenseDate(),
                requestDto.getTripId(), id);

        existingExpense.setTitle(requestDto.getTitle());
        existingExpense.setDescription(requestDto.getDescription());
        existingExpense.setAmount(requestDto.getAmount());
        existingExpense.setCategory(requestDto.getCategory());
        existingExpense.setExpenseDate(requestDto.getExpenseDate());
        existingExpense.setTripId(requestDto.getTripId());

        return ExpenseMapper.toResponse(expenseRepository.save(existingExpense));
    }

    @Override
    public void deleteExpense(Long id, CurrentUser actor) {
        Expense existingExpense = findOwnedExpense(id, actor);
        requireUnlocked(existingExpense);
        expenseRepository.delete(existingExpense);
    }

    private void requireUnlocked(Expense expense) {
        if (expense.getClaimId() != null) {
            throw new ExpenseConflictException(
                    "Expense is included in claim " + expense.getClaimId() + " and cannot be changed");
        }
    }

    private void requireOwnTrip(Long tripId, CurrentUser actor) {
        if (tripId == null) {
            return;
        }
        // Admins can read any trip, so ownership is checked here as well, not just left to trip-service.
        tripClient.findTrip(tripId)
                .filter(trip -> actor.owns(trip.ownerId()))
                .orElseThrow(() -> new InvalidExpenseException("Trip not found with id: " + tripId));
    }

    private Expense findOwnedExpense(Long id, CurrentUser actor) {
        Expense expense = findExpenseById(id);
        if (!actor.owns(expense.getOwnerId())) {
            if (actor.admin()) {
                throw new AccessDeniedException("Administrators can view but not modify other users' expenses");
            }
            throw notFound(id);
        }
        return expense;
    }

    private Expense findExpenseById(Long id) {
        return expenseRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private ExpenseNotFoundException notFound(Long id) {
        return new ExpenseNotFoundException("Expense not found with id: " + id);
    }

}
