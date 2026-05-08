package com.btc.claimservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private String description;

    @NotNull(message = "Claim amount is required")
    @PositiveOrZero(message = "Claim amount must be zero or positive")
    private BigDecimal claimAmount;

    @NotBlank(message = "Status is required")
    private String status;
}
