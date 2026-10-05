package com.btc.expenseservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.btc.expenseservice.client.TripClient;
import com.btc.expenseservice.entity.Expense;
import com.btc.expenseservice.repository.ExpenseRepository;
import com.btc.expenseservice.security.TestJwt;
import java.math.BigDecimal;
import java.time.LocalDate;
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

/** Expense filters and database-side aggregation (by category and month) through HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractExpenseQueryIntegration {

    private static final String RANGE = "from=2026-01-01&to=2026-03-31";

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseRepository expenseRepository;

    @MockitoBean
    private TripClient tripClient;

    private final String employee = TestJwt.bearer(10, "EMPLOYEE");
    private final String admin = TestJwt.bearer(1, "ADMIN");

    @BeforeEach
    void seed() {
        expense(10L, "MEAL", "10.50", "2026-01-15", 7L);
        expense(10L, "MEAL", "4.50", "2026-01-20", 7L);
        expense(10L, "HOTEL", "100.00", "2026-02-01", null);
        expense(20L, "TRAVEL", "50.00", "2026-02-10", null);
        expense(10L, "MEAL", "999.00", "2024-01-01", null); // outside the range
    }

    @AfterEach
    void cleanUp() {
        expenseRepository.deleteAll();
    }

    @Test
    void employeeSummaryCoversOnlyTheirExpensesInTheRange() throws Exception {
        mockMvc.perform(get("/expenses/summary?" + RANGE).header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.total").value(115.00))
                .andExpect(jsonPath("$.byCategory[0].category").value("HOTEL"))
                .andExpect(jsonPath("$.byCategory[1].category").value("MEAL"))
                .andExpect(jsonPath("$.byCategory[1].count").value(2))
                .andExpect(jsonPath("$.byCategory[1].total").value(15.00))
                .andExpect(jsonPath("$.byMonth.length()").value(2))
                .andExpect(jsonPath("$.byMonth[0].year").value(2026))
                .andExpect(jsonPath("$.byMonth[0].month").value(1))
                .andExpect(jsonPath("$.byMonth[0].total").value(15.00))
                .andExpect(jsonPath("$.byMonth[1].month").value(2));
    }

    @Test
    void adminSummaryIsCompanyWideOrPerEmployee() throws Exception {
        mockMvc.perform(get("/expenses/summary?" + RANGE).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.count").value(4)).andExpect(jsonPath("$.total").value(165.00));
        mockMvc.perform(get("/expenses/summary?ownerId=20&" + RANGE).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(jsonPath("$.count").value(1)).andExpect(jsonPath("$.byCategory[0].category").value("TRAVEL"));
        mockMvc.perform(get("/expenses/summary?ownerId=20").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void tripTotalsCoverAllOfTheTripsExpensesWithinTheCallersScope() throws Exception {
        mockMvc.perform(get("/expenses/summary?tripId=7").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(jsonPath("$.count").value(2)).andExpect(jsonPath("$.total").value(15.00));
        mockMvc.perform(get("/expenses/summary?tripId=7").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(20, "EMPLOYEE")))
                .andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void rangesAreValidatedAndBounded() throws Exception {
        mockMvc.perform(get("/expenses/summary?from=2026-03-01&to=2026-01-01").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses/summary?from=2020-01-01&to=2026-01-01").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The date range can be at most two years"));
        mockMvc.perform(get("/expenses/summary?from=not-a-date").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAreForAdministratorsOnly() throws Exception {
        for (String path : new String[] {"/expenses/reports/summary", "/expenses/reports/by-trip", "/expenses/reports/export.csv"}) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, employee)).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void spendingPerTripIsAggregatedAndPaged() throws Exception {
        expense(20L, "MEAL", "5.00", "2026-03-01", 8L);
        mockMvc.perform(get("/expenses/reports/by-trip?" + RANGE).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].tripId").value(7))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[0].total").value(15.00))
                .andExpect(jsonPath("$[1].tripId").value(8));
        mockMvc.perform(get("/expenses/reports/by-trip?size=1&" + RANGE).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "2")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void csvExportIsBoundedAndSafeForSpreadsheets() throws Exception {
        expense(10L, "OTHER", "1.00", "2026-03-05", null);
        Expense formula = expenseRepository.findAll().stream().filter(e -> "OTHER".equals(e.getCategory())).findFirst().orElseThrow();
        formula.setTitle("=HYPERLINK(\"http://evil\",\"x\")");
        expenseRepository.save(formula);

        String csv = mockMvc.perform(get("/expenses/reports/export.csv?" + RANGE).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("btc-expenses-2026-01-01-to-2026-03-31.csv")))
                .andReturn().getResponse().getContentAsString();
        String[] lines = csv.split("\r\n");
        assertThat(lines[0])
                .isEqualTo("expense_id,expense_date,owner_id,trip_id,claim_id,category,title,amount");
        assertThat(lines).hasSize(1 + 5);
        assertThat(csv).contains(",\"'=HYPERLINK(\"\"http://evil\"\",\"\"x\"\")\",1.00")
                .contains("2026-01-15,10,7,,MEAL,MEAL 10.50,10.50");
        mockMvc.perform(get("/expenses/reports/export.csv?from=2020-01-01&to=2026-01-01").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersCombineWithOwnership() throws Exception {
        mockMvc.perform(get("/expenses?category=meal&" + RANGE).header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(header().string("X-Total-Count", "2"));
        mockMvc.perform(get("/expenses?tripId=7").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(header().string("X-Total-Count", "2"));
        mockMvc.perform(get("/expenses?ownerId=20").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(header().string("X-Total-Count", "1"));
        mockMvc.perform(get("/expenses?ownerId=20").header(HttpHeaders.AUTHORIZATION, employee))
                .andExpect(status().isForbidden());
    }

    private void expense(Long owner, String category, String amount, String date, Long tripId) {
        expenseRepository.save(Expense.builder().title(category + " " + amount).category(category)
                .amount(new BigDecimal(amount)).expenseDate(LocalDate.parse(date)).ownerId(owner).tripId(tripId).build());
    }
}
