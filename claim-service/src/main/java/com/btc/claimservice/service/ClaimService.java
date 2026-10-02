package com.btc.claimservice.service;

import com.btc.claimservice.dto.ClaimRequestDto;
import com.btc.claimservice.dto.ClaimResponseDto;
import com.btc.claimservice.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClaimService {

    ClaimResponseDto createClaim(ClaimRequestDto requestDto, CurrentUser actor);

    ClaimResponseDto getClaimById(Long id, CurrentUser actor);

    Page<ClaimResponseDto> getAllClaims(CurrentUser actor, Pageable pageable);

    ClaimResponseDto updateClaim(Long id, ClaimRequestDto requestDto, CurrentUser actor);

    void deleteClaim(Long id, CurrentUser actor);

    ClaimResponseDto approveClaim(Long id, CurrentUser actor);

    ClaimResponseDto rejectClaim(Long id, CurrentUser actor);
}
