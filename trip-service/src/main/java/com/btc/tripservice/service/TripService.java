package com.btc.tripservice.service;

import com.btc.tripservice.dto.TripRequestDto;
import com.btc.tripservice.dto.TripResponseDto;
import com.btc.tripservice.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TripService {

    TripResponseDto createTrip(TripRequestDto requestDto, CurrentUser actor);

    TripResponseDto getTripById(Long id, CurrentUser actor);

    Page<TripResponseDto> getAllTrips(CurrentUser actor, Pageable pageable);

    TripResponseDto updateTrip(Long id, TripRequestDto requestDto, CurrentUser actor);

    void deleteTrip(Long id, CurrentUser actor);
}
