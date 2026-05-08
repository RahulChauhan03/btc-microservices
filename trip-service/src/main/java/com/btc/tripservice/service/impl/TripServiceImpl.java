package com.btc.tripservice.service.impl;

import com.btc.tripservice.dto.TripRequestDto;
import com.btc.tripservice.dto.TripResponseDto;
import com.btc.tripservice.entity.Trip;
import com.btc.tripservice.exception.DuplicateTripException;
import com.btc.tripservice.exception.TripNotFoundException;
import com.btc.tripservice.repository.TripRepository;
import com.btc.tripservice.service.TripService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;

    @Override
    public TripResponseDto createTrip(TripRequestDto requestDto) {
        if (tripRepository.existsByTripCode(requestDto.getTripCode())) {
            throw new DuplicateTripException("Trip already exists with trip code: " + requestDto.getTripCode());
        }

        Trip trip = Trip.builder()
                .tripCode(requestDto.getTripCode())
                .destination(requestDto.getDestination())
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .status(requestDto.getStatus())
                .budget(requestDto.getBudget())
                .build();

        return mapToResponse(tripRepository.save(trip));
    }

    @Override
    @Transactional(readOnly = true)
    public TripResponseDto getTripById(Long id) {
        return mapToResponse(findTripById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripResponseDto> getAllTrips() {
        return tripRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public TripResponseDto updateTrip(Long id, TripRequestDto requestDto) {
        Trip existingTrip = findTripById(id);

        if (tripRepository.existsByTripCodeAndIdNot(requestDto.getTripCode(), id)) {
            throw new DuplicateTripException("Trip already exists with trip code: " + requestDto.getTripCode());
        }

        existingTrip.setTripCode(requestDto.getTripCode());
        existingTrip.setDestination(requestDto.getDestination());
        existingTrip.setStartDate(requestDto.getStartDate());
        existingTrip.setEndDate(requestDto.getEndDate());
        existingTrip.setStatus(requestDto.getStatus());
        existingTrip.setBudget(requestDto.getBudget());

        return mapToResponse(tripRepository.save(existingTrip));
    }

    @Override
    public void deleteTrip(Long id) {
        Trip existingTrip = findTripById(id);
        tripRepository.delete(existingTrip);
    }

    private Trip findTripById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new TripNotFoundException("Trip not found with id: " + id));
    }

    private TripResponseDto mapToResponse(Trip trip) {
        return TripResponseDto.builder()
                .id(trip.getId())
                .tripCode(trip.getTripCode())
                .destination(trip.getDestination())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .status(trip.getStatus())
                .budget(trip.getBudget())
                .build();
    }
}
