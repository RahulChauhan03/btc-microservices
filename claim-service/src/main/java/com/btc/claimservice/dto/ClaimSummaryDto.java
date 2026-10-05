package com.btc.claimservice.dto;

import java.math.BigDecimal;
import java.util.List;

/** Claim counts and amounts by status for the caller's scope (administrators: everyone or one owner). */
public record ClaimSummaryDto(long total, BigDecimal totalAmount, long awaitingReview,
                              List<ClaimStatusTotalDto> byStatus) {
}
