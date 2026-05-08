package com.btc.claimservice.controller;

import com.btc.claimservice.dto.ClaimRequestDto;
import com.btc.claimservice.dto.ClaimResponseDto;
import com.btc.claimservice.service.ClaimService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @PostMapping
    public ResponseEntity<ClaimResponseDto> createClaim(@Valid @RequestBody ClaimRequestDto requestDto) {
        ClaimResponseDto createdClaim = claimService.createClaim(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdClaim);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponseDto> getClaimById(@PathVariable Long id) {
        return ResponseEntity.ok(claimService.getClaimById(id));
    }

    @GetMapping
    public ResponseEntity<List<ClaimResponseDto>> getAllClaims() {
        return ResponseEntity.ok(claimService.getAllClaims());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClaimResponseDto> updateClaim(@PathVariable Long id,
                                                        @Valid @RequestBody ClaimRequestDto requestDto) {
        return ResponseEntity.ok(claimService.updateClaim(id, requestDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClaim(@PathVariable Long id) {
        claimService.deleteClaim(id);
        return ResponseEntity.noContent().build();
    }
}
