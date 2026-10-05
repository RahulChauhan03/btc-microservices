package com.btc.expenseservice.repository;

import com.btc.expenseservice.entity.TravelPolicy;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelPolicyRepository extends JpaRepository<TravelPolicy, Long> {

    /** Policies covering the date (at most one, because periods never overlap). */
    @Query("select p from TravelPolicy p where p.effectiveFrom <= :date and (p.effectiveTo is null or p.effectiveTo >= :date)"
            + " order by p.effectiveFrom desc")
    List<TravelPolicy> findApplicable(@Param("date") LocalDate date);

    /** Other policies whose period intersects [from, to] (to = far future for open-ended). */
    @Query("select count(p) from TravelPolicy p where p.id <> :excludeId and p.effectiveFrom <= :to"
            + " and (p.effectiveTo is null or p.effectiveTo >= :from)")
    long countOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("excludeId") Long excludeId);

    List<TravelPolicy> findAllByOrderByEffectiveFromDesc();
}
