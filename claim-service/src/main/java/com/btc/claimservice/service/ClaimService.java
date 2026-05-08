package com.btc.claimservice.service;

import com.btc.claimservice.dto.ClaimRequestDto;
import com.btc.claimservice.dto.ClaimResponseDto;
import java.util.List;

public interface ClaimService {

    ClaimResponseDto createClaim(ClaimRequestDto requestDto);

    ClaimResponseDto getClaimById(Long id);

    List<ClaimResponseDto> getAllClaims();

    ClaimResponseDto updateClaim(Long id, ClaimRequestDto requestDto);

    void deleteClaim(Long id);
}
