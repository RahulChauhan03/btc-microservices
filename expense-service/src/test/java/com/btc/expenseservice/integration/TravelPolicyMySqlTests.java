package com.btc.expenseservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The travel policy suite on real MySQL/InnoDB (Flyway V4 + validation), plus the concurrency guarantee of the
 * trip limit, which relies on InnoDB locking. Skipped unless BTC_IT_MYSQL_* are set.
 */
@EnabledIfEnvironmentVariable(named = "BTC_IT_MYSQL_URL", matches = ".+")
class TravelPolicyMySqlTests extends AbstractTravelPolicyIntegration {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("BTC_IT_MYSQL_URL")
                + "/btc_it_expense_policy_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> System.getenv("BTC_IT_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("BTC_IT_MYSQL_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Test
    void concurrentExpensesCannotJointlyExceedTheTripLimit() throws Exception {
        createPolicy(POLICY.formatted("A", "100.00", "2026-01-01", "null"));
        for (int round = 0; round < 5; round++) {
            long trip = 900 + round;
            seedExpense("50.00", trip);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return mockMvc.perform(post("/expenses").header(HttpHeaders.AUTHORIZATION, employee)
                                .contentType(MediaType.APPLICATION_JSON).content(expenseJson("MEAL", "40.00", "2026-03-02", trip)))
                                .andReturn().getResponse().getStatus();
                    }));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get(30, TimeUnit.SECONDS));
                }
                assertThat(statuses).as("round %d", round).containsOnlyOnce(201);
                assertThat(statuses).as("loser is a policy violation or a lock conflict").containsAnyOf(422, 409);
                assertThat(expenseRepository.findAll().stream().filter(e -> e.getTripId() == trip).count()).isEqualTo(2);
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
