package com.btc.expenseservice.dto;

import java.math.BigDecimal;

public record MonthTotalDto(Integer year, Integer month, Long count, BigDecimal total) {
}
