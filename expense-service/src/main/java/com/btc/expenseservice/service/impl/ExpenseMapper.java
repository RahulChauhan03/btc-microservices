package com.btc.expenseservice.service.impl;

import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.entity.Expense;

final class ExpenseMapper {

    private ExpenseMapper() {
    }

    static ExpenseResponseDto toResponse(Expense expense) {
        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .tripId(expense.getTripId())
                .ownerId(expense.getOwnerId())
                .claimId(expense.getClaimId())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
