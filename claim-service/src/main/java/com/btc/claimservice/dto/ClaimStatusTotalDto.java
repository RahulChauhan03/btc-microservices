package com.btc.claimservice.dto;

import java.math.BigDecimal;

public record ClaimStatusTotalDto(String status, Long count, BigDecimal total) {
}
