package com.btc.claimservice.integration;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same suite on real MySQL/InnoDB, with the Flyway migrations applied and Hibernate validating the result.
 * Skipped unless BTC_IT_MYSQL_URL, BTC_IT_MYSQL_USER and BTC_IT_MYSQL_PASSWORD are set. Uses its own database,
 * btc_it_claim_service_db; never point it at real data.
 */
@EnabledIfEnvironmentVariable(named = "BTC_IT_MYSQL_URL", matches = ".+")
class ClaimWorkflowMySqlTests extends AbstractClaimWorkflowIntegrationTest {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("BTC_IT_MYSQL_URL")
                + "/btc_it_claim_service_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", () -> System.getenv("BTC_IT_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("BTC_IT_MYSQL_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }
}
