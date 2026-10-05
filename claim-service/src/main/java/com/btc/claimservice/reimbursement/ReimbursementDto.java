package com.btc.claimservice.reimbursement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** A reimbursement with its claim's identifying fields and the statuses it may move to next. */
public record ReimbursementDto(Long id, Long claimId, String claimNumber, String claimTitle, Long ownerId,
                               ReimbursementStatus status, BigDecimal amount, LocalDate paymentDate,
                               String paymentReference, LocalDateTime updatedAt, Set<ReimbursementStatus> allowedNext) {
}
