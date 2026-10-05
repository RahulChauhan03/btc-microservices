package com.btc.claimservice.reimbursement;

import com.btc.claimservice.audit.AuditService;
import com.btc.claimservice.client.NotificationMessage;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.exception.ClaimNotFoundException;
import com.btc.claimservice.exception.InvalidClaimStateException;
import com.btc.claimservice.exception.InvalidRequestException;
import com.btc.claimservice.outbox.ClaimOutbox;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.CurrentUser;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reimbursement records for approved claims. Employees see their own; only administrators update them, never for
 * their own claims, and only along ReimbursementStatus transitions. Each change is audited and the owner notified
 * in the same transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReimbursementService {

    private final ReimbursementRepository repository;
    private final ClaimRepository claimRepository;
    private final AuditService auditService;
    private final ClaimOutbox claimOutbox;

    /** Called by the claim approval, in its transaction; idempotent per claim. */
    public void openFor(Claim claim, Long approverId) {
        if (repository.findByClaimId(claim.getId()).isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        repository.save(Reimbursement.builder().claimId(claim.getId()).ownerId(claim.getOwnerId())
                .status(ReimbursementStatus.PENDING).amount(claim.getClaimAmount())
                .createdAt(now).updatedAt(now).updatedBy(approverId).build());
    }

    @Transactional(readOnly = true)
    public Page<ReimbursementDto> list(CurrentUser actor, Long ownerId, ReimbursementStatus status, Pageable pageable) {
        Long owner = actor.scopeOwner(ownerId);
        Specification<Reimbursement> spec = (root, query, cb) -> cb.conjunction();
        if (owner != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ownerId"), owner));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Page<Reimbursement> page = repository.findAll(spec, pageable);
        // One query for the claims on this page (no N+1).
        Map<Long, Claim> claims = claimRepository.findAllById(page.map(Reimbursement::getClaimId).getContent()).stream()
                .collect(Collectors.toMap(Claim::getId, Function.identity()));
        List<ReimbursementDto> items = page.getContent().stream().map(r -> toDto(r, claims.get(r.getClaimId()))).toList();
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ReimbursementDto forClaim(Long claimId, CurrentUser actor) {
        Claim claim = claimRepository.findById(claimId).orElseThrow(() -> notFound(claimId));
        if (!actor.admin() && !actor.owns(claim.getOwnerId())) {
            throw notFound(claimId);
        }
        Reimbursement reimbursement = repository.findByClaimId(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("No reimbursement for claim " + claimId + "; it is not approved"));
        return toDto(reimbursement, claim);
    }

    public ReimbursementDto update(Long claimId, ReimbursementUpdateDto request, CurrentUser actor) {
        actor.requireAdmin();
        Claim claim = claimRepository.findById(claimId).orElseThrow(() -> notFound(claimId));
        if (actor.owns(claim.getOwnerId())) {
            throw new AccessDeniedException("You cannot update the reimbursement of your own claim");
        }
        Reimbursement reimbursement = repository.findByClaimId(claimId)
                .orElseThrow(() -> new InvalidClaimStateException("Claim " + claimId + " is not approved, so it has no reimbursement"));
        ReimbursementStatus from = reimbursement.getStatus();
        ReimbursementStatus to = request.status();
        if (!from.canMoveTo(to)) {
            throw new InvalidClaimStateException("A " + from + " reimbursement cannot become " + to
                    + (from.next().isEmpty() ? " (PAID is final)" : "; allowed: " + from.next()));
        }
        if (to == ReimbursementStatus.PAID) {
            String reference = request.paymentReference() == null ? "" : request.paymentReference().trim();
            if (request.paymentDate() == null || reference.isEmpty()) {
                throw new InvalidRequestException("A paid reimbursement needs a payment date and a payment reference");
            }
            if (request.paymentDate().isAfter(LocalDate.now())) {
                throw new InvalidRequestException("The payment date cannot be in the future");
            }
            if (repository.existsByPaymentReferenceAndIdNot(reference, reimbursement.getId())) {
                throw new InvalidClaimStateException("Payment reference " + reference + " is already recorded for another reimbursement");
            }
            reimbursement.setPaymentDate(request.paymentDate());
            reimbursement.setPaymentReference(reference);
        }
        reimbursement.setStatus(to);
        reimbursement.setUpdatedAt(LocalDateTime.now());
        reimbursement.setUpdatedBy(actor.id());
        Reimbursement saved = repository.saveAndFlush(reimbursement);

        auditService.record(actor.id(), "REIMBURSEMENT_STATUS_CHANGED", "CLAIM", claimId,
                "Reimbursement of claim %s (%s): %s → %s%s".formatted(claim.getClaimNumber(), money(saved.getAmount()),
                        from, to, to == ReimbursementStatus.PAID ? ", paid " + saved.getPaymentDate() : ""));
        if (claim.getOwnerId() != null) {
            claimOutbox.enqueueNotification(claimId, NotificationMessage.toUser(claim.getOwnerId(), actor.id(),
                    "REIMBURSEMENT_UPDATED", "Reimbursement " + to.name().toLowerCase(Locale.ROOT),
                    message(claim, saved), "/reimbursements"));
        }
        return toDto(saved, claim);
    }

    private static String message(Claim claim, Reimbursement reimbursement) {
        String amount = money(reimbursement.getAmount());
        return switch (reimbursement.getStatus()) {
            case PAID -> "The reimbursement of %s for claim %s was recorded as paid on %s (reference %s)."
                    .formatted(amount, claim.getClaimNumber(), reimbursement.getPaymentDate(), reimbursement.getPaymentReference());
            case PROCESSING -> "The reimbursement of %s for claim %s is being processed.".formatted(amount, claim.getClaimNumber());
            case FAILED -> "The reimbursement of %s for claim %s could not be completed; finance will retry it."
                    .formatted(amount, claim.getClaimNumber());
            case PENDING -> "The reimbursement for claim %s is pending.".formatted(claim.getClaimNumber());
        };
    }

    private static ReimbursementDto toDto(Reimbursement r, Claim claim) {
        return new ReimbursementDto(r.getId(), r.getClaimId(), claim == null ? null : claim.getClaimNumber(),
                claim == null ? null : claim.getTitle(), r.getOwnerId(), r.getStatus(), r.getAmount(), r.getPaymentDate(),
                r.getPaymentReference(), r.getUpdatedAt(), r.getStatus().next());
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }

    private static ClaimNotFoundException notFound(Long claimId) {
        return new ClaimNotFoundException("Claim not found with id: " + claimId);
    }
}
