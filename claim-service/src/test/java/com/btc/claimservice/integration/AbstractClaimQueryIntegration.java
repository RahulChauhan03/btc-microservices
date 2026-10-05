package com.btc.claimservice.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.entity.Claim;
import com.btc.claimservice.repository.ClaimRepository;
import com.btc.claimservice.security.TestJwt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Claim list filters and status summary through HTTP against a real database. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractClaimQueryIntegration {

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClaimRepository claimRepository;

    @MockitoBean
    private ExpenseLockClient expenseLockClient;

    private final String employee = TestJwt.bearer(10, "EMPLOYEE");
    private final String admin = TestJwt.bearer(1, "ADMIN");

    @BeforeEach
    void seed() {
        claim(10L, "SUBMITTED", "10.00", 5L);
        claim(10L, "APPROVED", "20.00", 5L);
        claim(10L, "REJECTED", "5.00", null);
        claim(20L, "SUBMITTED", "7.00", 99L);
        claim(20L, "PENDING", "3.00", null);
    }

    @AfterEach
    void cleanUp() {
        claimRepository.deleteAll();
    }

    @Test
    void filtersAreScopedAndValidated() throws Exception {
        mockMvc.perform(get("/claims?status=SUBMITTED").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(get("/claims?status=submitted,pending&sort=claimAmount,desc").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "3")).andExpect(jsonPath("$[0].claimAmount").value(10.00));
        mockMvc.perform(get("/claims?tripId=99").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(get("/claims?tripId=99").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(header().string("X-Total-Count", "0"));
        mockMvc.perform(get("/claims?ownerId=20").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/claims?status=PAID").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summaryCountsAndAmountsByStatus() throws Exception {
        mockMvc.perform(get("/claims/summary").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.awaitingReview").value(1))
                .andExpect(jsonPath("$.totalAmount").value(35.00))
                .andExpect(jsonPath("$.byStatus[?(@.status=='APPROVED')].total").value(20.00));
        mockMvc.perform(get("/claims/summary").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.total").value(5)).andExpect(jsonPath("$.awaitingReview").value(3));
        mockMvc.perform(get("/claims/summary?ownerId=20").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void statusReportIsForAdministratorsAndFiltersBySubmissionDate() throws Exception {
        String today = LocalDate.now().toString();
        mockMvc.perform(get("/claims/reports/status?from=" + today + "&to=" + today).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalAmount").value(45.00))
                .andExpect(jsonPath("$.byStatus[?(@.status=='SUBMITTED')].count").value(2));
        mockMvc.perform(get("/claims/reports/status?from=2020-01-01&to=2020-12-31").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/claims/reports/status").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
    }

    private void claim(Long owner, String status, String amount, Long tripId) {
        claimRepository.save(Claim.builder().claimNumber("C-" + UUID.randomUUID().toString().substring(0, 8))
                .title("Claim").claimAmount(new BigDecimal(amount)).status(status).ownerId(owner).tripId(tripId)
                .expenseIds(new LinkedHashSet<>(List.of(owner * 100 + amount.hashCode() % 50))).build());
    }
}
