package com.btc.claimservice.controller;

import com.btc.claimservice.dto.ClaimStatusReportDto;
import com.btc.claimservice.dto.ClaimStatusTotalDto;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.CurrentUser;
import com.btc.claimservice.web.DateRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only claim reporting; the date range (by submission date) is bounded to two years. */
@RestController
@RequestMapping("/claims/reports")
@RequiredArgsConstructor
public class ClaimReportController {

    private final ClaimRepository claimRepository;

    @GetMapping("/status")
    @Transactional(readOnly = true)
    public ResponseEntity<ClaimStatusReportDto> statusBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser.from(jwt).requireAdmin();
        DateRange range = DateRange.resolve(from, to);
        List<ClaimStatusTotalDto> rows = claimRepository.totalsByStatusSubmittedBetween(
                range.from().atStartOfDay(), range.to().plusDays(1).atStartOfDay());
        long total = rows.stream().mapToLong(ClaimStatusTotalDto::count).sum();
        BigDecimal amount = rows.stream().map(ClaimStatusTotalDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return ResponseEntity.ok(new ClaimStatusReportDto(range.from(), range.to(), total, amount, rows));
    }
}
