package com.btc.expenseservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Internal (claim-service only): lock these expenses of this owner for a claim. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseLockRequestDto {

    @NotNull(message = "Owner id is required")
    private Long ownerId;

    @NotEmpty(message = "At least one expense is required")
    @Size(max = 100, message = "A claim can cover at most 100 expenses")
    private List<@NotNull(message = "Expense ids must not be null") Long> expenseIds;
}
