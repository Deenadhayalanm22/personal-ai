package com.apps.deen_sa;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

import java.time.Duration;

@Testcontainers
@SpringBootTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = NONE)
public abstract class IntegrationTestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("integration_db")
            .withUsername("test")
            .withPassword("test")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60));

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // HikariCP configuration to prevent connection timeout issues during long-running fuzz tests
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20"); // Increased from 10 to handle fuzz test load
        registry.add("spring.datasource.hikari.minimum-idle", () -> "5"); // Increased from 2
        registry.add("spring.datasource.hikari.connection-timeout", () -> "60000"); // Increased from 30s to 60s
        registry.add("spring.datasource.hikari.max-lifetime", () -> "1800000"); // 30 minutes
        registry.add("spring.datasource.hikari.idle-timeout", () -> "300000"); // Reduced from 10min to 5min to release idle connections faster
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "30000"); // Reduced from 60s to 30s for earlier detection
        registry.add("spring.datasource.hikari.validation-timeout", () -> "5000");
        registry.add("spring.datasource.hikari.connection-test-query", () -> "SELECT 1");
    }
}
