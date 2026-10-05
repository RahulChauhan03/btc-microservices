package com.btc.userservice.audit;

import com.btc.userservice.security.CurrentUser;
import com.btc.userservice.web.DateRange;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side audit trail. record() must join the transaction of the audited change (MANDATORY), so the record
 * exists exactly when the change committed. Summaries hold ids, statuses and amounts only: no secrets.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String SOURCE = "users";

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorId, String action, String targetType, Long targetId, String summary) {
        String safe = summary.length() <= 500 ? summary : summary.substring(0, 499) + "…";
        repository.save(new AuditLog(null, actorId, action, targetType, targetId, safe, LocalDateTime.now()));
    }

    /** Administrators only; optional filters, bounded date range, newest first. */
    @Transactional(readOnly = true)
    public Page<AuditLogDto> search(CurrentUser actor, String action, Long actorId, Long targetId, LocalDate from,
                                    LocalDate to, Pageable pageable) {
        actor.requireAdmin();
        DateRange range = DateRange.resolve(from, to);
        LocalDateTime start = range.from().atStartOfDay();
        LocalDateTime end = range.to().plusDays(1).atStartOfDay();
        Specification<AuditLog> spec = (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), start), cb.lessThan(root.get("createdAt"), end));
        if (action != null && !action.isBlank()) {
            String normalized = action.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), normalized));
        }
        if (actorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (targetId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("targetId"), targetId));
        }
        return repository.findAll(spec, pageable).map(log -> AuditLogDto.from(log, SOURCE));
    }
}
