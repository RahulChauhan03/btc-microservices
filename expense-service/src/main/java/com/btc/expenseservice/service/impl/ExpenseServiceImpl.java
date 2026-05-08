package com.btc.expenseservice.service.impl;

import com.btc.expenseservice.dto.ExpenseRequestDto;
import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.exception.ExpenseNotFoundException;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.service.ExpenseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;

    @Override
    public ExpenseResponseDto createExpense(ExpenseRequestDto requestDto) {
        Expense expense = Expense.builder()
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .amount(requestDto.getAmount())
                .category(requestDto.getCategory())
                .expenseDate(requestDto.getExpenseDate())
                .build();

        return mapToResponse(expenseRepository.save(expense));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponseDto getExpenseById(Long id) {
        return mapToResponse(findExpenseById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseResponseDto> getAllExpenses() {
        return expenseRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ExpenseResponseDto updateExpense(Long id, ExpenseRequestDto requestDto) {
        Expense existingExpense = findExpenseById(id);

        existingExpense.setTitle(requestDto.getTitle());
        existingExpense.setDescription(requestDto.getDescription());
        existingExpense.setAmount(requestDto.getAmount());
        existingExpense.setCategory(requestDto.getCategory());
        existingExpense.setExpenseDate(requestDto.getExpenseDate());

        return mapToResponse(expenseRepository.save(existingExpense));
    }

    @Override
    public void deleteExpense(Long id) {
        Expense existingExpense = findExpenseById(id);
        expenseRepository.delete(existingExpense);
    }

    private Expense findExpenseById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with id: " + id));
    }

    private ExpenseResponseDto mapToResponse(Expense expense) {
        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .expenseDate(expense.getExpenseDate())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
