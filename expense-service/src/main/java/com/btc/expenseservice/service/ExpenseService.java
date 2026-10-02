package com.btc.expenseservice.service;

import com.btc.expenseservice.dto.ExpenseRequestDto;
import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExpenseService {

    ExpenseResponseDto createExpense(ExpenseRequestDto requestDto, CurrentUser actor);

    ExpenseResponseDto getExpenseById(Long id, CurrentUser actor);

    /** Lists visible expenses, optionally only those on {@code tripId}. */
    Page<ExpenseResponseDto> getAllExpenses(CurrentUser actor, Long tripId, Pageable pageable);

    ExpenseResponseDto updateExpense(Long id, ExpenseRequestDto requestDto, CurrentUser actor);

    void deleteExpense(Long id, CurrentUser actor);
}
