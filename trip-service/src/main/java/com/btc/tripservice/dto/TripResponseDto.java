package com.btc.tripservice.dto;

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
public class TripResponseDto {

    private Long id;
    private String tripCode;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private BigDecimal budget;
}
