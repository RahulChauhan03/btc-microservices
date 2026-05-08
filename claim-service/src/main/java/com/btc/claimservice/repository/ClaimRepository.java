package com.btc.claimservice.repository;

import com.btc.claimservice.entity.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {

    boolean existsByClaimNumber(String claimNumber);

    boolean existsByClaimNumberAndIdNot(String claimNumber, Long id);
}
