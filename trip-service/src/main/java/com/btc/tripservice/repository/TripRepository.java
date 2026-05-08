package com.btc.tripservice.repository;

import com.btc.tripservice.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    boolean existsByTripCode(String tripCode);

    boolean existsByTripCodeAndIdNot(String tripCode, Long id);
}
