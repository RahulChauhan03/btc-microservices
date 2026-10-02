package com.btc.expenseservice.controller;

import com.btc.expenseservice.dto.ExpenseRequestDto;
import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.security.CurrentUser;
import com.btc.expenseservice.service.ExpenseService;
import com.btc.expenseservice.web.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private static final Set<String> SORTABLE =
            Set.of("id", "title", "category", "amount", "expenseDate", "createdAt");

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponseDto> createExpense(@Valid @RequestBody ExpenseRequestDto requestDto,
                                                            @AuthenticationPrincipal Jwt jwt) {
        ExpenseResponseDto createdExpense = expenseService.createExpense(requestDto, CurrentUser.from(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(createdExpense);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> getExpenseById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(expenseService.getExpenseById(id, CurrentUser.from(jwt)));
    }

    @GetMapping
    public ResponseEntity<List<ExpenseResponseDto>> getAllExpenses(
            @RequestParam(required = false) Long tripId,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SORT) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return PageRequests.toResponse(expenseService.getAllExpenses(
                CurrentUser.from(jwt), tripId, PageRequests.of(page, size, sort, SORTABLE)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponseDto> updateExpense(@PathVariable Long id,
                                                            @Valid @RequestBody ExpenseRequestDto requestDto,
                                                            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(expenseService.updateExpense(id, requestDto, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        expenseService.deleteExpense(id, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }
}
