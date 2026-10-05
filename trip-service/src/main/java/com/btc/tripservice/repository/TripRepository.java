package com.btc.tripservice.repository;

import com.btc.tripservice.dto.StatusCountDto;
import com.btc.tripservice.entity.Trip;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long>, JpaSpecificationExecutor<Trip> {

    @Query("select new com.btc.tripservice.dto.StatusCountDto(t.status, count(t)) from Trip t"
            + " where (:ownerId is null or t.ownerId = :ownerId) group by t.status order by t.status")
    List<StatusCountDto> countByStatus(@Param("ownerId") Long ownerId);

    @Query("select count(t) from Trip t where (:ownerId is null or t.ownerId = :ownerId)"
            + " and t.status in :statuses and t.endDate >= :today")
    long countUpcoming(@Param("ownerId") Long ownerId, @Param("statuses") Collection<String> statuses,
                       @Param("today") LocalDate today);

    Page<Trip> findAllByOwnerId(Long ownerId, Pageable pageable);

    boolean existsByTripCode(String tripCode);

    boolean existsByTripCodeAndIdNot(String tripCode, Long id);
}
