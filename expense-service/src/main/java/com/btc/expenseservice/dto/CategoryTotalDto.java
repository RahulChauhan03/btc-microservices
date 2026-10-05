package com.btc.expenseservice.dto;

import java.math.BigDecimal;

public record CategoryTotalDto(String category, Long count, BigDecimal total) {
}
