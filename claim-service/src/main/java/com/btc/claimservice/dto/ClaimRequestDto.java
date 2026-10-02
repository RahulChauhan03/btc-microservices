package com.btc.claimservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Claim input. The amount, status, owner and reviewer are decided by the server; any such fields
 * in the request body are ignored.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimRequestDto {

    @NotBlank(message = "Claim number is required")
    private String claimNumber;

    @NotBlank(message = "Title is required")
    private String title;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotEmpty(message = "At least one expense is required")
    @Size(max = 100, message = "A claim can cover at most 100 expenses")
    private List<@NotNull(message = "Expense ids must not be null") Long> expenseIds;
}
