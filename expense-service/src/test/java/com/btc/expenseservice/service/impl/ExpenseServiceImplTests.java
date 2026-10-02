package com.btc.expenseservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.btc.expenseservice.client.TripClient;
import com.btc.expenseservice.client.TripClient.TripSummary;
import com.btc.expenseservice.dto.ExpenseRequestDto;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.exception.ExpenseConflictException;
import com.btc.expenseservice.exception.ExpenseNotFoundException;
import com.btc.expenseservice.exception.InvalidExpenseException;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.security.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

class ExpenseServiceImplTests {

    private static final CurrentUser OWNER = new CurrentUser(10L, false);
    private static final CurrentUser OTHER = new CurrentUser(20L, false);
    private static final CurrentUser ADMIN = new CurrentUser(1L, true);

    private final ExpenseRepository expenseRepository = mock(ExpenseRepository.class);
    private final TripClient tripClient = mock(TripClient.class);
    private final ExpenseServiceImpl expenseService = new ExpenseServiceImpl(expenseRepository, tripClient);

    @BeforeEach
    void setUp() {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(expenseRepository.findById(5L)).thenReturn(Optional.of(expense(5L, OWNER.id())));
        when(tripClient.findTrip(100L)).thenReturn(Optional.of(new TripSummary(100L, OWNER.id())));
        when(tripClient.findTrip(200L)).thenReturn(Optional.of(new TripSummary(200L, OTHER.id())));
        when(tripClient.findTrip(300L)).thenReturn(Optional.empty());
    }

    @Test
    void expenseIsOwnedByCallerAndMayReferenceOwnTrip() {
        var created = expenseService.createExpense(request(100L), OWNER);

        assertThat(created.getOwnerId()).isEqualTo(OWNER.id());
        assertThat(created.getTripId()).isEqualTo(100L);
        assertThat(expenseService.createExpense(request(null), OWNER).getTripId()).isNull();
    }

    @Test
    void tripOfAnotherUserOrMissingTripIsRejected() {
        assertThatThrownBy(() -> expenseService.createExpense(request(200L), OWNER))
                .isInstanceOf(InvalidExpenseException.class);
        assertThatThrownBy(() -> expenseService.createExpense(request(300L), OWNER))
                .isInstanceOf(InvalidExpenseException.class);
        // Admins can read every trip, but still cannot attach their expenses to someone else's trip.
        assertThatThrownBy(() -> expenseService.createExpense(request(200L), ADMIN))
                .isInstanceOf(InvalidExpenseException.class);
        assertThatThrownBy(() -> expenseService.updateExpense(5L, request(200L), OWNER))
                .isInstanceOf(InvalidExpenseException.class);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void otherUsersCannotReadUpdateOrDeleteExpense() {
        assertThatThrownBy(() -> expenseService.getExpenseById(5L, OTHER)).isInstanceOf(ExpenseNotFoundException.class);
        assertThatThrownBy(() -> expenseService.updateExpense(5L, request(null), OTHER))
                .isInstanceOf(ExpenseNotFoundException.class);
        assertThatThrownBy(() -> expenseService.deleteExpense(5L, OTHER)).isInstanceOf(ExpenseNotFoundException.class);
        verify(expenseRepository, never()).delete(any());
    }

    @Test
    void adminReadsAllButModifiesOnlyOwnExpenses() {
        when(expenseRepository.findAll(Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(expense(5L, OWNER.id()), expense(6L, OTHER.id()))));
        when(expenseRepository.findAllByOwnerId(OWNER.id(), Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(expense(5L, OWNER.id()))));

        assertThat(expenseService.getAllExpenses(ADMIN, null, Pageable.unpaged())).hasSize(2);
        assertThat(expenseService.getAllExpenses(OWNER, null, Pageable.unpaged())).hasSize(1);
        assertThat(expenseService.getExpenseById(5L, ADMIN).getId()).isEqualTo(5L);
        assertThatThrownBy(() -> expenseService.deleteExpense(5L, ADMIN)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void expenseLockedByClaimCannotBeUpdatedOrDeleted() {
        Expense locked = expense(9L, OWNER.id());
        locked.setClaimId(70L);
        when(expenseRepository.findById(9L)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> expenseService.updateExpense(9L, request(null), OWNER))
                .isInstanceOf(ExpenseConflictException.class);
        assertThatThrownBy(() -> expenseService.deleteExpense(9L, OWNER)).isInstanceOf(ExpenseConflictException.class);
        verify(expenseRepository, never()).save(any());
        verify(expenseRepository, never()).delete(any());
    }

    private static ExpenseRequestDto request(Long tripId) {
        return ExpenseRequestDto.builder().title("Taxi").amount(new BigDecimal("12.50")).category("TRANSPORT")
                .expenseDate(LocalDate.now()).tripId(tripId).build();
    }

    private static Expense expense(Long id, Long ownerId) {
        return Expense.builder().id(id).title("Hotel").amount(BigDecimal.TEN).category("HOTEL")
                .expenseDate(LocalDate.now()).ownerId(ownerId).build();
    }
}
