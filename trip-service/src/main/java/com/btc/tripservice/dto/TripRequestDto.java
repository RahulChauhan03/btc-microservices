package com.btc.tripservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
public class TripRequestDto {

    @NotBlank(message = "Trip code is required")
    private String tripCode;

    @NotBlank(message = "Destination is required")
    private String destination;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "PLANNED|IN_PROGRESS|COMPLETED|CANCELLED",
            message = "Status must be PLANNED, IN_PROGRESS, COMPLETED or CANCELLED")
    private String status;

    @NotNull(message = "Budget is required")
    @Positive(message = "Budget must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Budget must have at most 10 digits and 2 decimals")
    private BigDecimal budget;

    @JsonIgnore
    @AssertTrue(message = "End date must be on or after start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
