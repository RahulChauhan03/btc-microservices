package com.btc.claimservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Claims submitted in a date range, by status (administrators only). */
public record ClaimStatusReportDto(LocalDate from, LocalDate to, long total, BigDecimal totalAmount,
                                   List<ClaimStatusTotalDto> byStatus) {
}
