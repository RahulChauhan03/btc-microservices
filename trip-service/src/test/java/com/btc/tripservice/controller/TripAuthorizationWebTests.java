package com.btc.tripservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.tripservice.client.TripExpensesClient;
import com.btc.tripservice.config.SecurityConfig;
import com.btc.tripservice.entity.Trip;
import com.btc.tripservice.repository.TripRepository;
import com.btc.tripservice.security.TestJwt;
import com.btc.tripservice.service.impl.TripServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TripController.class)
@Import({SecurityConfig.class, TripServiceImpl.class})
class TripAuthorizationWebTests {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripRepository tripRepository;

    @MockitoBean
    private TripExpensesClient tripExpensesClient;

    @Test
    void changingTheIdInTheUrlDoesNotExposeAnotherUsersTrip() throws Exception {
        when(tripRepository.findById(5L)).thenReturn(Optional.of(Trip.builder().id(5L).ownerId(10L).build()));

        mockMvc.perform(get("/trips/5").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(20, "EMPLOYEE")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/trips/5").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(20, "EMPLOYEE")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/trips/5").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(1, "ADMIN")))
                .andExpect(status().isForbidden());
        verify(tripRepository, never()).delete(any(Trip.class));
    }

    @Test
    void clientSuppliedOwnerIdIsIgnored() throws Exception {
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/trips").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.now(), LocalDate.now().plusDays(2), "1000") .replace("{", "{\"ownerId\":99,")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(10));

        ArgumentCaptor<Trip> saved = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(10L);
    }

    @Test
    void invalidDatesAmountsAndStatusesAreRejected() throws Exception {
        String token = TestJwt.bearer(10, "EMPLOYEE");
        for (String body : new String[] {
                body(LocalDate.now(), LocalDate.now().minusDays(1), "1000"),
                body(LocalDate.now(), LocalDate.now(), "0"),
                body(LocalDate.now(), LocalDate.now(), "1000").replace("PLANNED", "APPROVED")}) {
            mockMvc.perform(post("/trips").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(tripRepository, never()).save(any());
    }

    @Test
    void deletingTripWithExpensesReturnsConflict() throws Exception {
        when(tripRepository.findById(5L)).thenReturn(Optional.of(Trip.builder().id(5L).ownerId(10L).build()));
        when(tripExpensesClient.hasExpenses(5L)).thenReturn(true);

        mockMvc.perform(delete("/trips/5").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "EMPLOYEE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        verify(tripRepository, never()).delete(any(Trip.class));
    }

    private static String body(LocalDate start, LocalDate end, String budget) {
        return "{\"tripCode\":\"T-9\",\"destination\":\"Goa\",\"startDate\":\"" + start + "\",\"endDate\":\"" + end
                + "\",\"status\":\"PLANNED\",\"budget\":" + new BigDecimal(budget) + "}";
    }
}
