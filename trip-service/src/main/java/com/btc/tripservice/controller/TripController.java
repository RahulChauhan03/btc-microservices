package com.btc.tripservice.controller;

import com.btc.tripservice.dto.TripListFilter;
import com.btc.tripservice.dto.TripRequestDto;
import com.btc.tripservice.dto.TripResponseDto;
import com.btc.tripservice.dto.TripSummaryDto;
import com.btc.tripservice.security.CurrentUser;
import com.btc.tripservice.service.TripService;
import com.btc.tripservice.web.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripController {

    private static final Set<String> SORTABLE =
            Set.of("id", "tripCode", "destination", "startDate", "endDate", "status", "budget");

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<TripResponseDto> createTrip(@Valid @RequestBody TripRequestDto requestDto,
                                                      @AuthenticationPrincipal Jwt jwt) {
        TripResponseDto createdTrip = tripService.createTrip(requestDto, CurrentUser.from(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTrip);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponseDto> getTripById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(tripService.getTripById(id, CurrentUser.from(jwt)));
    }

    /** Counts by status in the caller's scope (administrators: everyone, or one owner via ownerId). */
    @GetMapping("/summary")
    public ResponseEntity<TripSummaryDto> getSummary(@RequestParam(required = false) Long ownerId,
                                                     @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(tripService.getSummary(CurrentUser.from(jwt), ownerId));
    }

    @GetMapping
    public ResponseEntity<List<TripResponseDto>> getAllTrips(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean upcoming,
            @RequestParam(required = false) List<Long> ids,
            @RequestParam(defaultValue = PageRequests.DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = PageRequests.DEFAULT_SORT) String sort,
            @AuthenticationPrincipal Jwt jwt) {
        return PageRequests.toResponse(
                tripService.getAllTrips(CurrentUser.from(jwt), new TripListFilter(ownerId, status, upcoming, ids),
                        PageRequests.of(page, size, sort, SORTABLE)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TripResponseDto> updateTrip(@PathVariable Long id,
                                                      @Valid @RequestBody TripRequestDto requestDto,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(tripService.updateTrip(id, requestDto, CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        tripService.deleteTrip(id, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }
}
