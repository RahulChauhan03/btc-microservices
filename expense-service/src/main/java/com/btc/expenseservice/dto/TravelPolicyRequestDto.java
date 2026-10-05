package com.btc.expenseservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Limits are amounts in the application currency, with at most two decimals (like expense amounts). */
public record TravelPolicyRequestDto(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "Currency must be an ISO 4217 code, e.g. USD") String currency,
        @DecimalMin(value = "0.01", message = "Trip limit must be positive")
        @Digits(integer = 10, fraction = 2, message = "Trip limit must have at most 10 digits and 2 decimals") BigDecimal tripLimit,
        @NotNull(message = "Effective from is required") LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Map<@Pattern(regexp = "TRAVEL|HOTEL|MEAL|TRANSPORT|OTHER", message = "Unknown expense category") String,
            @NotNull @DecimalMin(value = "0.01", message = "Category limits must be positive")
            @Digits(integer = 10, fraction = 2, message = "Category limits must have at most 2 decimals") BigDecimal> categoryLimits) {
}
