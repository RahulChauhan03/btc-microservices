package com.btc.expenseservice.controller;

import com.btc.expenseservice.dto.ExpenseSummaryDto;
import com.btc.expenseservice.dto.TripSpendDto;
import com.btc.expenseservice.security.CurrentUser;
import com.btc.expenseservice.service.impl.ExpenseReportService;
import com.btc.expenseservice.web.DateRange;
import com.btc.expenseservice.web.PageRequests;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only reports (checked in ExpenseReportService); date ranges are bounded to two years. */
@RestController
@RequestMapping("/expenses/reports")
@RequiredArgsConstructor
public class ExpenseReportController {

    private final ExpenseReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<ExpenseSummaryDto> summary(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(reportService.summary(CurrentUser.from(jwt), ownerId, from, to));
    }

    /** Spending per trip, largest first. */
    @GetMapping("/by-trip")
    public ResponseEntity<List<TripSpendDto>> byTrip(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        PageRequests.of(page, size, PageRequests.DEFAULT_SORT, Set.of("id")); // validates page and size
        return PageRequests.toResponse(reportService.byTrip(CurrentUser.from(jwt), from, to, PageRequest.of(page, size)));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal Jwt jwt) {
        DateRange range = DateRange.resolve(from, to);
        byte[] body = reportService.exportCsv(CurrentUser.from(jwt), range.from(), range.to()).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("btc-expenses-" + range.from() + "-to-" + range.to() + ".csv").build().toString())
                .body(body);
    }
}
