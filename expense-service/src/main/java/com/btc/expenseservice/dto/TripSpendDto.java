package com.btc.expenseservice.dto;

import java.math.BigDecimal;

public record TripSpendDto(Long tripId, Long count, BigDecimal total) {
}
