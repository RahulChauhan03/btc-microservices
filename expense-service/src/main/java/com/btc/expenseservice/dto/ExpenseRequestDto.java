package com.btc.expenseservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
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
public class ExpenseRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 digits and 2 decimals")
    private BigDecimal amount;

    @NotBlank(message = "Category is required")
    @Pattern(regexp = "TRAVEL|HOTEL|MEAL|TRANSPORT|OTHER",
            message = "Category must be TRAVEL, HOTEL, MEAL, TRANSPORT or OTHER")
    private String category;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    /** Optional; must reference a trip owned by the caller. */
    private Long tripId;
}
