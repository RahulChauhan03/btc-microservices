package com.btc.expenseservice.controller;

import com.btc.expenseservice.dto.TravelPolicyRequestDto;
import com.btc.expenseservice.dto.TravelPolicyResponseDto;
import com.btc.expenseservice.security.CurrentUser;
import com.btc.expenseservice.service.impl.TravelPolicyService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Travel policy: every signed-in user can read it; only administrators create or change it (service check). */
@RestController
@RequestMapping("/expenses/policies")
@RequiredArgsConstructor
public class TravelPolicyController {

    private final TravelPolicyService policyService;

    @GetMapping
    public ResponseEntity<List<TravelPolicyResponseDto>> list() {
        return ResponseEntity.ok(policyService.list());
    }

    /** The policy that applies on a date (default today); 204 when none does. */
    @GetMapping("/applicable")
    public ResponseEntity<TravelPolicyResponseDto> applicable(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return policyService.applicableOn(date == null ? LocalDate.now() : date)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<TravelPolicyResponseDto> create(@Valid @RequestBody TravelPolicyRequestDto request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.create(request, CurrentUser.from(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TravelPolicyResponseDto> update(@PathVariable Long id, @Valid @RequestBody TravelPolicyRequestDto request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(policyService.update(id, request, CurrentUser.from(jwt)));
    }
}
