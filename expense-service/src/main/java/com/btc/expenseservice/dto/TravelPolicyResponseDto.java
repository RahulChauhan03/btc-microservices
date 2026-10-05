package com.btc.expenseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record TravelPolicyResponseDto(Long id, String name, String currency, BigDecimal tripLimit,
                                      LocalDate effectiveFrom, LocalDate effectiveTo,
                                      Map<String, BigDecimal> categoryLimits, LocalDateTime updatedAt, boolean active) {
}
