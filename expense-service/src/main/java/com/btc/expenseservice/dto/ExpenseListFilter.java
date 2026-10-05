package com.btc.expenseservice.dto;

import java.time.LocalDate;

/** Optional list filters; ownerId is honoured for administrators only (see CurrentUser.scopeOwner). */
public record ExpenseListFilter(Long ownerId, Long tripId, String category, LocalDate from, LocalDate to) {
}
