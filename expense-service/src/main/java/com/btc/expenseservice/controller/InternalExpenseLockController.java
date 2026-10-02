package com.btc.expenseservice.controller;

import com.btc.expenseservice.dto.ExpenseLockRequestDto;
import com.btc.expenseservice.dto.ExpenseResponseDto;
import com.btc.expenseservice.service.impl.ExpenseLockService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API for claim-service. Requires a SERVICE token (see SecurityConfig); user tokens are
 * refused here, and the API gateway does not expose /expenses/internal/** at all.
 */
@RestController
@RequestMapping("/expenses/internal/claims/{claimId}/locks")
@RequiredArgsConstructor
public class InternalExpenseLockController {

    private final ExpenseLockService expenseLockService;

    @PutMapping
    public ResponseEntity<List<ExpenseResponseDto>> lock(@PathVariable Long claimId,
                                                         @Valid @RequestBody ExpenseLockRequestDto requestDto) {
        return ResponseEntity.ok(expenseLockService.lockForClaim(claimId, requestDto.getOwnerId(),
                requestDto.getExpenseIds()));
    }

    @DeleteMapping
    public ResponseEntity<Void> release(@PathVariable Long claimId) {
        expenseLockService.releaseClaim(claimId);
        return ResponseEntity.noContent().build();
    }
}
