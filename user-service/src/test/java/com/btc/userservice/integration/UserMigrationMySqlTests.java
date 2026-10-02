package com.btc.userservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.btc.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flyway migrations on real MySQL, then Hibernate schema validation (the context fails to start if they
 * disagree). Skipped unless BTC_IT_MYSQL_URL, BTC_IT_MYSQL_USER and BTC_IT_MYSQL_PASSWORD are set.
 * Uses its own database, btc_it_user_service_db; never point it at real data.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "BTC_IT_MYSQL_URL", matches = ".+")
class UserMigrationMySqlTests {

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("BTC_IT_MYSQL_URL")
                + "/btc_it_user_service_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true");
        registry.add("spring.datasource.username", () -> System.getenv("BTC_IT_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("BTC_IT_MYSQL_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository repository;

    @Test
    void migrationsApplyAndMatchTheEntities() {
        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = 0", Integer.class);
        assertThat(failed).isZero();
        assertThat(repository.count()).isGreaterThanOrEqualTo(0);
    }
}
