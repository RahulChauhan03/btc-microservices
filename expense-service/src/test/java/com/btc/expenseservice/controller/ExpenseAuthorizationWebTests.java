package com.btc.expenseservice.controller;

import com.btc.expenseservice.service.impl.TravelPolicyService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.expenseservice.client.TripClient;
import com.btc.expenseservice.config.SecurityConfig;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.security.TestJwt;
import com.btc.expenseservice.service.impl.ExpenseServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExpenseController.class)
@Import({SecurityConfig.class, ExpenseServiceImpl.class})
class ExpenseAuthorizationWebTests {

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelPolicyService travelPolicyService;

    @MockitoBean
    private ExpenseRepository expenseRepository;

    @MockitoBean
    private TripClient tripClient;

    @Test
    void anotherUsersExpenseIsNotFound() throws Exception {
        when(expenseRepository.findById(5L)).thenReturn(Optional.of(Expense.builder().id(5L).ownerId(10L).build()));

        mockMvc.perform(get("/expenses/5").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(20, "EMPLOYEE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerIdInBodyIsIgnoredAndInvalidValuesAreRejected() throws Exception {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String token = TestJwt.bearer(10, "EMPLOYEE");

        mockMvc.perform(post("/expenses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(body("25.00", "MEAL").replace("{", "{\"ownerId\":99,")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(10));

        for (String invalid : new String[] {body("0", "MEAL"), body("-5", "MEAL"), body("10", "CASINO")}) {
            mockMvc.perform(post("/expenses").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void tripThatTheCallerDoesNotOwnIsRejected() throws Exception {
        when(tripClient.findTrip(200L)).thenReturn(Optional.of(new TripClient.TripSummary(200L, 20L)));

        mockMvc.perform(post("/expenses").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(10, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("25.00", "MEAL").replace("}", ",\"tripId\":200}")))
                .andExpect(status().isBadRequest());
        verify(expenseRepository, never()).save(any());
    }

    private static String body(String amount, String category) {
        return "{\"title\":\"Lunch\",\"amount\":" + amount + ",\"category\":\"" + category
                + "\",\"expenseDate\":\"2026-09-01\"}";
    }
}
