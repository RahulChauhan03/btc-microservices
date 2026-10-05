package com.btc.claimservice.reimbursement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, Long>, JpaSpecificationExecutor<Reimbursement> {

    Optional<Reimbursement> findByClaimId(Long claimId);

    boolean existsByPaymentReferenceAndIdNot(String paymentReference, Long id);
}
