package com.btc.tripservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.btc.tripservice.client.TripExpensesClient;
import com.btc.tripservice.dto.TripListFilter;
import com.btc.tripservice.dto.TripRequestDto;
import com.btc.tripservice.entity.Trip;
import com.btc.tripservice.exception.DependencyUnavailableException;
import com.btc.tripservice.exception.InvalidRequestException;
import com.btc.tripservice.exception.TripInUseException;
import com.btc.tripservice.exception.TripNotFoundException;
import com.btc.tripservice.repository.TripRepository;
import com.btc.tripservice.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

class TripServiceImplTests {

    private static final TripListFilter NO_FILTER = new TripListFilter(null, null, false);

    private static final CurrentUser OWNER = new CurrentUser(10L, false);
    private static final CurrentUser OTHER = new CurrentUser(20L, false);
    private static final CurrentUser ADMIN = new CurrentUser(1L, true);

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripExpensesClient tripExpensesClient = mock(TripExpensesClient.class);
    private final TripServiceImpl tripService = new TripServiceImpl(tripRepository, tripExpensesClient);

    @BeforeEach
    void setUp() {
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tripRepository.findById(5L)).thenReturn(Optional.of(trip(5L, OWNER.id())));
        when(tripRepository.findById(6L)).thenReturn(Optional.of(trip(6L, null)));
    }

    @Test
    void ownerIsTakenFromAuthenticatedUser() {
        assertThat(tripService.createTrip(request(), OWNER).getOwnerId()).isEqualTo(OWNER.id());
    }

    @Test
    void listsAreScopedToOwnerExceptForAdmin() {
        when(tripRepository.findAll(any(Specification.class), eq(Pageable.unpaged())))
                .thenReturn(new PageImpl<>(List.of(trip(5L, OWNER.id()))));

        assertThat(tripService.getAllTrips(OWNER, NO_FILTER, Pageable.unpaged())).hasSize(1);
        assertThat(tripService.getAllTrips(ADMIN, new TripListFilter(OTHER.id(), null, false), Pageable.unpaged())).hasSize(1);
        assertThatThrownBy(() -> tripService.getAllTrips(OWNER, new TripListFilter(OTHER.id(), null, false), Pageable.unpaged()))
                .as("employee asking for another owner's trips").isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tripService.getAllTrips(ADMIN, new TripListFilter(null, "BOGUS", false), Pageable.unpaged()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void otherUsersCannotReadUpdateOrDeleteTrip() {
        assertThatThrownBy(() -> tripService.getTripById(5L, OTHER)).isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> tripService.updateTrip(5L, request(), OTHER)).isInstanceOf(TripNotFoundException.class);
        assertThatThrownBy(() -> tripService.deleteTrip(5L, OTHER)).isInstanceOf(TripNotFoundException.class);
        verify(tripRepository, never()).save(any());
        verify(tripRepository, never()).delete(any(Trip.class));
    }

    @Test
    void adminCanReadButNotModifyOtherUsersTrips() {
        assertThat(tripService.getTripById(5L, ADMIN).getId()).isEqualTo(5L);
        assertThatThrownBy(() -> tripService.updateTrip(5L, request(), ADMIN)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tripService.deleteTrip(5L, ADMIN)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void legacyTripsWithoutOwnerAreVisibleOnlyToAdmin() {
        assertThatThrownBy(() -> tripService.getTripById(6L, OWNER)).isInstanceOf(TripNotFoundException.class);
        assertThat(tripService.getTripById(6L, ADMIN).getOwnerId()).isNull();
        assertThatThrownBy(() -> tripService.deleteTrip(6L, OWNER)).isInstanceOf(TripNotFoundException.class);
    }

    @Test
    void ownerCanUpdateAndDeleteOwnTrip() {
        assertThat(tripService.updateTrip(5L, request(), OWNER).getDestination()).isEqualTo("Pune");
        tripService.deleteTrip(5L, OWNER);
        verify(tripRepository).delete(any(Trip.class));
    }

    @Test
    void tripWithExpensesCannotBeDeleted() {
        when(tripExpensesClient.hasExpenses(5L)).thenReturn(true);

        assertThatThrownBy(() -> tripService.deleteTrip(5L, OWNER)).isInstanceOf(TripInUseException.class);
        verify(tripRepository, never()).delete(any(Trip.class));
    }

    @Test
    void tripIsNotDeletedWhenExpenseServiceCannotConfirm() {
        when(tripExpensesClient.hasExpenses(5L)).thenThrow(new DependencyUnavailableException("down", null));

        assertThatThrownBy(() -> tripService.deleteTrip(5L, OWNER)).isInstanceOf(DependencyUnavailableException.class);
        verify(tripRepository, never()).delete(any(Trip.class));
    }

    @Test
    void expenseCheckIsNotEvenMadeForTripsTheCallerCannotDelete() {
        assertThatThrownBy(() -> tripService.deleteTrip(5L, OTHER)).isInstanceOf(TripNotFoundException.class);
        verify(tripExpensesClient, never()).hasExpenses(any());
    }

    private static TripRequestDto request() {
        return TripRequestDto.builder().tripCode("T-1").destination("Pune").startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(1)).status("PLANNED").budget(BigDecimal.TEN).build();
    }

    private static Trip trip(Long id, Long ownerId) {
        return Trip.builder().id(id).tripCode("T-" + id).destination("Goa").startDate(LocalDate.now())
                .endDate(LocalDate.now()).status("PLANNED").budget(BigDecimal.ONE).ownerId(ownerId).build();
    }
}
