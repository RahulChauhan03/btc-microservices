package com.btc.claimservice.reimbursement;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** PAID requires paymentDate (not in the future) and paymentReference; other statuses ignore them. */
public record ReimbursementUpdateDto(
        @NotNull(message = "Status is required") ReimbursementStatus status,
        LocalDate paymentDate,
        @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._/-]*", message = "Payment reference may contain letters, digits and . _ / - only")
        String paymentReference) {
}
