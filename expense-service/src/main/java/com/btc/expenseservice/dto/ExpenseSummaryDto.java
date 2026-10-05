package com.btc.expenseservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Totals computed by the database for one owner (or everyone, for administrators) over a date range. */
public record ExpenseSummaryDto(LocalDate from, LocalDate to, long count, BigDecimal total,
                                List<CategoryTotalDto> byCategory, List<MonthTotalDto> byMonth) {
}
