package com.btc.claimservice.service.impl;

import com.btc.claimservice.dto.ClaimRequestDto;
import com.btc.claimservice.dto.ClaimResponseDto;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.exception.ClaimNotFoundException;
import com.btc.claimservice.exception.DuplicateClaimException;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.service.ClaimService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ClaimServiceImpl implements ClaimService {

    private final ClaimRepository claimRepository;

    @Override
    public ClaimResponseDto createClaim(ClaimRequestDto requestDto) {
        if (claimRepository.existsByClaimNumber(requestDto.getClaimNumber())) {
            throw new DuplicateClaimException("Claim already exists with claim number: " + requestDto.getClaimNumber());
        }

        Claim claim = Claim.builder()
                .claimNumber(requestDto.getClaimNumber())
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .claimAmount(requestDto.getClaimAmount())
                .status(requestDto.getStatus())
                .build();

        return mapToResponse(claimRepository.save(claim));
    }

    @Override
    @Transactional(readOnly = true)
    public ClaimResponseDto getClaimById(Long id) {
        return mapToResponse(findClaimById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimResponseDto> getAllClaims() {
        return claimRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public ClaimResponseDto updateClaim(Long id, ClaimRequestDto requestDto) {
        Claim existingClaim = findClaimById(id);

        if (claimRepository.existsByClaimNumberAndIdNot(requestDto.getClaimNumber(), id)) {
            throw new DuplicateClaimException("Claim already exists with claim number: " + requestDto.getClaimNumber());
        }

        existingClaim.setClaimNumber(requestDto.getClaimNumber());
        existingClaim.setTitle(requestDto.getTitle());
        existingClaim.setDescription(requestDto.getDescription());
        existingClaim.setClaimAmount(requestDto.getClaimAmount());
        existingClaim.setStatus(requestDto.getStatus());

        return mapToResponse(claimRepository.save(existingClaim));
    }

    @Override
    public void deleteClaim(Long id) {
        Claim existingClaim = findClaimById(id);
        claimRepository.delete(existingClaim);
    }

    private Claim findClaimById(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new ClaimNotFoundException("Claim not found with id: " + id));
    }

    private ClaimResponseDto mapToResponse(Claim claim) {
        return ClaimResponseDto.builder()
                .id(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .title(claim.getTitle())
                .description(claim.getDescription())
                .claimAmount(claim.getClaimAmount())
                .status(claim.getStatus())
                .submittedAt(claim.getSubmittedAt())
                .build();
    }
}
