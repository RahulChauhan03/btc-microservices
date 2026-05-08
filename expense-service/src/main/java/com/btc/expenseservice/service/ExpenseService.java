package com.btc.expenseservice.service;

import com.btc.expenseservice.dto.ExpenseRequestDto;
import com.btc.expenseservice.dto.ExpenseResponseDto;
import java.util.List;

public interface ExpenseService {

    ExpenseResponseDto createExpense(ExpenseRequestDto requestDto);

    ExpenseResponseDto getExpenseById(Long id);

    List<ExpenseResponseDto> getAllExpenses();

    ExpenseResponseDto updateExpense(Long id, ExpenseRequestDto requestDto);

    void deleteExpense(Long id);
}
