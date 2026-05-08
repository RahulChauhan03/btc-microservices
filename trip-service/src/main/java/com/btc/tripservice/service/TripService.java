package com.btc.tripservice.service;

import com.btc.tripservice.dto.TripRequestDto;
import com.btc.tripservice.dto.TripResponseDto;
import java.util.List;

public interface TripService {

    TripResponseDto createTrip(TripRequestDto requestDto);

    TripResponseDto getTripById(Long id);

    List<TripResponseDto> getAllTrips();

    TripResponseDto updateTrip(Long id, TripRequestDto requestDto);

    void deleteTrip(Long id);
}
