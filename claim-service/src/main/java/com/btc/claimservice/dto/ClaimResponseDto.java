package com.btc.claimservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public class ClaimResponseDto {

    private Long id;
    private String claimNumber;
    private String title;
    private String description;
    private BigDecimal claimAmount;
    private String status;
    private LocalDateTime submittedAt;
}
