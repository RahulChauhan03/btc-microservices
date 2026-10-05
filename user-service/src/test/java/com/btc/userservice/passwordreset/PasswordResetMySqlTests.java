package com.btc.userservice.passwordreset;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The same suite on real MySQL/InnoDB with Flyway V1..V2 applied, Hibernate validating, and the production
 * JDBC time zone (serverTimezone=UTC). Skipped unless BTC_IT_MYSQL_* are set; never point it at real data.
 */
@EnabledIfEnvironmentVariable(named = "BTC_IT_MYSQL_URL", matches = ".+")
class PasswordResetMySqlTests extends AbstractPasswordResetIntegrationTest {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("BTC_IT_MYSQL_URL")
                + "/btc_it_user_reset_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> System.getenv("BTC_IT_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("BTC_IT_MYSQL_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }
}
