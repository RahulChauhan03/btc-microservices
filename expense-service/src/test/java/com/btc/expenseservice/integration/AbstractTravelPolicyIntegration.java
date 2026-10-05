package com.btc.expenseservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.expenseservice.audit.AuditLogRepository;
import com.btc.expenseservice.client.TripClient.TripSummary;
import com.btc.expenseservice.client.TripClient;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.repository.TravelPolicyRepository;
import com.btc.expenseservice.security.TestJwt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Travel policy management and its server-side enforcement on expenses, through HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractTravelPolicyIntegration {

    static final long OWNER = 10L;
    static final long TRIP = 70L;
    static final String POLICY = """
            {"name":"Standard %s","currency":"USD","tripLimit":%s,"effectiveFrom":"%s","effectiveTo":%s,
             "categoryLimits":{"MEAL":50.00,"HOTEL":200.00}}""";

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ExpenseRepository expenseRepository;

    @Autowired
    TravelPolicyRepository policyRepository;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    TripClient tripClient;

    final String employee = TestJwt.bearer(OWNER, "EMPLOYEE");
    final String admin = TestJwt.bearer(1, "ADMIN");

    @BeforeEach
    void trips() {
        when(tripClient.findTrip(anyLong())).thenAnswer(call -> Optional.of(new TripSummary(call.getArgument(0), OWNER)));
    }

    @Autowired
    AuditLogRepository auditLogRepository;

    @AfterEach
    void cleanUp() {
        auditLogRepository.deleteAll();
        expenseRepository.deleteAll();
        policyRepository.deleteAll();
    }

    @Test
    void everyoneReadsPoliciesButOnlyAdministratorsWriteThem() throws Exception {
        String body = POLICY.formatted("A", "100.00", "2026-01-01", "null");
        mockMvc.perform(post("/expenses/policies").header(HttpHeaders.AUTHORIZATION, employee)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/expenses/policies").header(HttpHeaders.AUTHORIZATION, employee)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        long id = createPolicy(body);
        mockMvc.perform(put("/expenses/policies/" + id).header(HttpHeaders.AUTHORIZATION, employee)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());

        mockMvc.perform(get("/expenses/policies").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryLimits.MEAL").value(50.00))
                .andExpect(jsonPath("$[0].tripLimit").value(100.00));
        mockMvc.perform(get("/expenses/policies/applicable?date=2026-06-01").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mockMvc.perform(get("/expenses/policies/applicable?date=2025-06-01").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/expenses/policies/" + id).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(POLICY.formatted("A2", "150.00", "2026-01-01", "null")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/expenses/audit-logs").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$[0].action").value("TRAVEL_POLICY_UPDATED"))
                .andExpect(jsonPath("$[0].summary").value(containsString("trip limit 100.00")))
                .andExpect(jsonPath("$[0].summary").value(containsString("trip limit 150.00")))
                .andExpect(jsonPath("$[1].action").value("TRAVEL_POLICY_CREATED"));
        mockMvc.perform(get("/expenses/audit-logs").header(HttpHeaders.AUTHORIZATION, employee)).andExpect(status().isForbidden());
    }

    @Test
    void policiesAreValidated() throws Exception {
        createPolicy(POLICY.formatted("A", "100.00", "2026-01-01", "\"2026-06-30\""));

        write(POLICY.formatted("B", "100.00", "2026-06-01", "null")).andExpect(status().isConflict());
        write(POLICY.formatted("C", "100.00", "2026-08-01", "\"2026-07-01\"")).andExpect(status().isBadRequest());
        write(POLICY.formatted("D", "-5", "2026-07-01", "null")).andExpect(status().isBadRequest());
        write(POLICY.formatted("E", "1.001", "2026-07-01", "null")).andExpect(status().isBadRequest());
        write(POLICY.formatted("F", "100", "2026-07-01", "null").replace("USD", "EUR")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("application currency USD")));
        write(POLICY.formatted("G", "100", "2026-07-01", "null").replace("\"MEAL\"", "\"SPA\"")).andExpect(status().isBadRequest());
        write(POLICY.formatted("H", "null", "2026-07-01", "null")).andExpect(status().isCreated());
    }

    @Test
    void categoryLimitsRejectExpensesOverTheLimitWithoutChangingAmounts() throws Exception {
        createPolicy(POLICY.formatted("A", "null", "2026-01-01", "null"));

        expense("MEAL", "80.00", "2026-03-01", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("This meal expense of $80.00 exceeds the travel policy “Standard A” limit of $50.00 per expense."));
        expense("MEAL", "50.00", "2026-03-01", null).andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(50.00));
        expense("TRAVEL", "999.00", "2026-03-01", null).andExpect(status().isCreated());
        expense("MEAL", "80.00", "2025-12-31", null).andExpect(status().isCreated()); // before the policy starts
        assertThat(expenseRepository.count()).isEqualTo(3);
    }

    @Test
    void tripLimitCountsTheWholeTripAndUpdatesReplaceTheirOldAmount() throws Exception {
        createPolicy(POLICY.formatted("A", "100.00", "2026-01-01", "null"));

        long first = id(expense("HOTEL", "60.00", "2026-03-01", TRIP).andExpect(status().isCreated()));
        expense("MEAL", "45.00", "2026-03-02", TRIP).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("trip's spending to $105.00")));
        expense("MEAL", "40.00", "2026-03-02", TRIP).andExpect(status().isCreated());
        mockMvc.perform(put("/expenses/" + first).header(HttpHeaders.AUTHORIZATION, employee).contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("HOTEL", "55.00", "2026-03-01", TRIP)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/expenses/" + first).header(HttpHeaders.AUTHORIZATION, employee).contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("HOTEL", "61.00", "2026-03-01", TRIP)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(expenseRepository.findById(first).orElseThrow().getAmount()).isEqualByComparingTo("55.00");
    }

    long createPolicy(String body) throws Exception {
        return id(write(body).andExpect(status().isCreated()));
    }

    ResultActions write(String body) throws Exception {
        return mockMvc.perform(post("/expenses/policies").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions expense(String category, String amount, String date, Long tripId) throws Exception {
        return mockMvc.perform(post("/expenses").header(HttpHeaders.AUTHORIZATION, employee)
                .contentType(MediaType.APPLICATION_JSON).content(expenseJson(category, amount, date, tripId)));
    }

    static String expenseJson(String category, String amount, String date, Long tripId) {
        return """
                {"title":"%s","amount":%s,"category":"%s","expenseDate":"%s","tripId":%s}"""
                .formatted(category + " " + amount, amount, category, date, tripId);
    }

    long id(ResultActions result) throws Exception {
        JsonNode json = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    void seedExpense(String amount, Long tripId) {
        expenseRepository.save(Expense.builder().title("seed").category("OTHER").amount(new BigDecimal(amount))
                .expenseDate(LocalDate.parse("2026-03-01")).ownerId(OWNER).tripId(tripId).build());
    }
}
