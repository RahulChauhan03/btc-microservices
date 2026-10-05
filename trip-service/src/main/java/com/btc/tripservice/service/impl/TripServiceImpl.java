package com.btc.tripservice.service.impl;

import com.btc.tripservice.client.TripExpensesClient;
import com.btc.tripservice.dto.StatusCountDto;
import com.btc.tripservice.dto.TripListFilter;
import com.btc.tripservice.dto.TripRequestDto;
import com.btc.tripservice.dto.TripResponseDto;
import com.btc.tripservice.dto.TripSummaryDto;
import com.btc.tripservice.entity.Trip;
import com.btc.tripservice.exception.DuplicateTripException;
import com.btc.tripservice.exception.InvalidRequestException;
import com.btc.tripservice.exception.TripInUseException;
import com.btc.tripservice.exception.TripNotFoundException;
import com.btc.tripservice.repository.TripRepository;
import com.btc.tripservice.security.CurrentUser;
import com.btc.tripservice.service.TripService;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ownership rules: users see and change only their own trips. Administrators can read every trip
 * but, like everyone else, can modify only their own. Other users' trips are reported as not found.
 * A trip that expenses still reference cannot be deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TripServiceImpl implements TripService {

    private static final List<String> ACTIVE_STATUSES = List.of("PLANNED", "IN_PROGRESS");
    private static final Set<String> KNOWN_STATUSES = Set.of("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED");

    private final TripRepository tripRepository;
    private final TripExpensesClient tripExpensesClient;

    @Override
    public TripResponseDto createTrip(TripRequestDto requestDto, CurrentUser actor) {
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
                .ownerId(actor.id())
                .build();

        return mapToResponse(tripRepository.save(trip));
    }

    @Override
    @Transactional(readOnly = true)
    public TripResponseDto getTripById(Long id, CurrentUser actor) {
        return mapToResponse(findReadableTrip(id, actor));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TripResponseDto> getAllTrips(CurrentUser actor, TripListFilter filter, Pageable pageable) {
        Long ownerId = actor.scopeOwner(filter.ownerId());
        Specification<Trip> spec = (root, query, cb) -> cb.conjunction();
        if (ownerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ownerId"), ownerId));
        }
        if (filter.status() != null && !filter.status().isBlank()) {
            String status = requireKnownStatus(filter.status());
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (filter.ids() != null && !filter.ids().isEmpty()) {
            if (filter.ids().size() > TripListFilter.MAX_IDS) {
                throw new InvalidRequestException("At most " + TripListFilter.MAX_IDS + " ids per request");
            }
            spec = spec.and((root, query, cb) -> root.get("id").in(filter.ids()));
        }
        if (filter.upcoming()) {
            LocalDate today = LocalDate.now();
            spec = spec.and((root, query, cb) -> cb.and(root.get("status").in(ACTIVE_STATUSES),
                    cb.greaterThanOrEqualTo(root.get("endDate"), today)));
        }
        return tripRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TripSummaryDto getSummary(CurrentUser actor, Long requestedOwnerId) {
        Long ownerId = actor.scopeOwner(requestedOwnerId);
        List<StatusCountDto> byStatus = tripRepository.countByStatus(ownerId);
        long total = byStatus.stream().mapToLong(StatusCountDto::count).sum();
        return new TripSummaryDto(total, tripRepository.countUpcoming(ownerId, ACTIVE_STATUSES, LocalDate.now()), byStatus);
    }

    private static String requireKnownStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!KNOWN_STATUSES.contains(normalized)) {
            throw new InvalidRequestException("Unknown trip status: " + status);
        }
        return normalized;
    }

    @Override
    public TripResponseDto updateTrip(Long id, TripRequestDto requestDto, CurrentUser actor) {
        Trip existingTrip = findOwnedTrip(id, actor);

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
    public void deleteTrip(Long id, CurrentUser actor) {
        Trip trip = findOwnedTrip(id, actor);
        if (tripExpensesClient.hasExpenses(id)) {
            throw new TripInUseException("Trip " + id + " still has expenses; move or delete them first");
        }
        tripRepository.delete(trip);
    }

    private Trip findReadableTrip(Long id, CurrentUser actor) {
        Trip trip = findTripById(id);
        if (!actor.admin() && !actor.owns(trip.getOwnerId())) {
            throw notFound(id);
        }
        return trip;
    }

    private Trip findOwnedTrip(Long id, CurrentUser actor) {
        Trip trip = findTripById(id);
        if (!actor.owns(trip.getOwnerId())) {
            if (actor.admin()) {
                throw new AccessDeniedException("Administrators can view but not modify other users' trips");
            }
            throw notFound(id);
        }
        return trip;
    }

    private Trip findTripById(Long id) {
        return tripRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    private TripNotFoundException notFound(Long id) {
        return new TripNotFoundException("Trip not found with id: " + id);
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
                .ownerId(trip.getOwnerId())
                .build();
    }
}
