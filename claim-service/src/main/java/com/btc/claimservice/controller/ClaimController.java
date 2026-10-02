package com.btc.claimservice.controller;

import com.btc.claimservice.dto.ClaimRequestDto;
import com.btc.claimservice.dto.ClaimResponseDto;
import com.btc.claimservice.security.CurrentUser;
import com.btc.claimservice.service.ClaimService;
import com.btc.claimservice.web.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimController {

    private static final Set<String> SORTABLE =
            Set.of("id", "claimNumber", "title", "claimAmount", "status", "submittedAt");

    private final ClaimService claimService;

    @PostMapping
    public ResponseEntity<ClaimResponseDto> createClaim(@Valid @RequestBody ClaimRequestDto requestDto,
                                                        @AuthenticationPrincipal Jwt jwt) {
        ClaimResponseDto createdClaim = claimService.createClaim(requestDto, CurrentUser.from(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(createdClaim);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponseDto> getClaimById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(claimService.getClaimById(id, CurrentUser.from(jwt)));
    }

    @GetMapping
    public ResponseEntity<List<ClaimResponseDto>> getAllClaims(
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SORT) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return PageRequests.toResponse(
                claimService.getAllClaims(CurrentUser.from(jwt), PageRequests.of(page, size, sort, SORTABLE)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimResponseDto> updateClaim(@PathVariable Long id,
                                                        @Valid @RequestBody ClaimRequestDto requestDto,
                                                        @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(claimService.updateClaim(id, requestDto, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClaim(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        claimService.deleteClaim(id, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ClaimResponseDto> approveClaim(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(claimService.approveClaim(id, CurrentUser.from(jwt)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ClaimResponseDto> rejectClaim(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(claimService.rejectClaim(id, CurrentUser.from(jwt)));
    }
}
