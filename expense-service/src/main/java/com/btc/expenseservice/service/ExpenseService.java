package com.btc.expenseservice.service;

import com.btc.expenseservice.dto.ExpenseListFilter;
import com.btc.expenseservice.dto.ExpenseRequestDto;
import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.dto.ExpenseSummaryDto;
import com.btc.expenseservice.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExpenseService {

    ExpenseResponseDto createExpense(ExpenseRequestDto requestDto, CurrentUser actor);

    ExpenseResponseDto getExpenseById(Long id, CurrentUser actor);

    /** Lists visible expenses, optionally only those on {@code tripId}. */
    Page<ExpenseResponseDto> getAllExpenses(CurrentUser actor, ExpenseListFilter filter, Pageable pageable);

    ExpenseSummaryDto getSummary(CurrentUser actor, Long ownerId, Long tripId, LocalDate from, LocalDate to);

    ExpenseResponseDto updateExpense(Long id, ExpenseRequestDto requestDto, CurrentUser actor);

    void deleteExpense(Long id, CurrentUser actor);
}
