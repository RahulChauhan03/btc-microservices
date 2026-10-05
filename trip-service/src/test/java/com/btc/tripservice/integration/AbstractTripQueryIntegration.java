package com.btc.tripservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.tripservice.entity.Trip;
import com.btc.tripservice.repository.TripRepository;
import com.btc.tripservice.security.TestJwt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Trip list filters, ownership scoping and summary counts through HTTP against a real database. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractTripQueryIntegration {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TripRepository tripRepository;

    private final String employee = TestJwt.bearer(10, "EMPLOYEE");
    private final String admin = TestJwt.bearer(1, "ADMIN");

    @BeforeEach
    void seed() {
        LocalDate today = LocalDate.now();
        trip(10L, "PLANNED", today.plusDays(5), today.plusDays(10));
        trip(10L, "IN_PROGRESS", today.minusDays(1), today.plusDays(2));
        trip(10L, "COMPLETED", today.minusDays(20), today.minusDays(15));
        trip(10L, "PLANNED", today.minusDays(9), today.minusDays(8)); // planned but already over: not upcoming
        trip(20L, "PLANNED", today.plusDays(3), today.plusDays(4));
    }

    @AfterEach
    void cleanUp() {
        tripRepository.deleteAll();
    }

    @Test
    void employeesSeeOnlyTheirOwnTripsAndCannotAskForOthers() throws Exception {
        mockMvc.perform(get("/trips").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "4"));
        mockMvc.perform(get("/trips?ownerId=20").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/trips/summary?ownerId=20").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtersCombineWithOwnershipAndPaging() throws Exception {
        mockMvc.perform(get("/trips?upcoming=true&sort=startDate,asc").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[1].status").value("PLANNED"));
        mockMvc.perform(get("/trips?ownerId=20").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1")).andExpect(jsonPath("$[0].ownerId").value(20));
        mockMvc.perform(get("/trips?status=completed").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(get("/trips?status=BOGUS").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void batchLookupByIdsStaysWithinTheCallersScope() throws Exception {
        String all = tripRepository.findAll().stream().map(t -> String.valueOf(t.getId()))
                .collect(Collectors.joining(","));
        mockMvc.perform(get("/trips?ids=" + all).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "5"));
        mockMvc.perform(get("/trips?ids=" + all).header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(header().string("X-Total-Count", "4"));
    }

    @Test
    void summaryIsScopedToTheCaller() throws Exception {
        mockMvc.perform(get("/trips/summary").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.upcoming").value(2))
                .andExpect(jsonPath("$.byStatus[?(@.status=='PLANNED')].count").value(2));
        mockMvc.perform(get("/trips/summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.upcoming").value(3));
    }

    private void trip(Long owner, String status, LocalDate start, LocalDate end) {
        tripRepository.save(Trip.builder().tripCode("T-" + UUID.randomUUID().toString().substring(0, 8))
                .destination("Pune").startDate(start).endDate(end).status(status).budget(new BigDecimal("500.00"))
                .ownerId(owner).build());
    }
}
