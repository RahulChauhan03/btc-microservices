package com.btc.claimservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.btc.claimservice.client.ExpenseLockClient;
import com.btc.claimservice.outbox.ClaimOutbox;
import com.btc.claimservice.outbox.OutboxEventRepository;
import com.btc.claimservice.outbox.OutboxEventType;
import com.btc.claimservice.outbox.OutboxProcessor;
import com.btc.claimservice.outbox.OutboxStatus;
import com.btc.claimservice.security.TestJwt;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Regression: with the production JDBC URL (serverTimezone=UTC) and a service JVM that is not on UTC, outbox
 * timestamps must still be stored as true UTC, so the SQL in docs/operations/phase-5-claim-outbox.md (which
 * compares with UTC_TIMESTAMP()) agrees with the processor. They used to be stored shifted by the JVM's offset.
 * Real MySQL only (H2 does no zone conversion); enabled with BTC_IT_MYSQL_*.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BTC_IT_MYSQL_URL", matches = ".+")
class ClaimOutboxTimezoneMySqlTests {

    private static TimeZone originalZone;

    @BeforeAll
    static void runServiceInIndianTime() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestJwt.register(registry);
        registry.add("spring.datasource.url", () -> System.getenv("BTC_IT_MYSQL_URL")
                + "/btc_it_claim_tz_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> System.getenv("BTC_IT_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("BTC_IT_MYSQL_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private ClaimOutbox claimOutbox;

    @Autowired
    private OutboxProcessor processor;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ExpenseLockClient expenseLockClient;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void timestampsAreStoredInUtcAndAgreeWithDatabaseSql() {
        String eventId = new TransactionTemplate(transactionManager).execute(status ->
                claimOutbox.enqueueRelease(OutboxEventType.CLAIM_REJECTED, 9500L, List.of(1L)));

        Long secondsBehindUtc = jdbcTemplate.queryForObject("SELECT TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6)) "
                + "FROM claim_outbox WHERE event_id = ?", Long.class, eventId);
        assertThat(secondsBehindUtc).as("created_at vs UTC_TIMESTAMP()").isBetween(-5L, 60L);

        // An operator making an event due "now" in SQL must make it due for the processor too.
        jdbcTemplate.update("UPDATE claim_outbox SET next_attempt_at = UTC_TIMESTAMP(6) WHERE event_id = ?", eventId);
        assertThat(processor.processDue()).isEqualTo(1);
        verify(expenseLockClient).releaseClaim(9500L);
        assertThat(outboxEventRepository.findByEventId(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.COMPLETED);
    }
}
