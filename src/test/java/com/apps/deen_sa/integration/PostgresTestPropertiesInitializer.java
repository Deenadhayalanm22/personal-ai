package com.apps.deen_sa.integration;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Connects opt-in integration tests to the local containers in podman-compose.yml. */
public class PostgresTestPropertiesInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.datasource.url=jdbc:postgresql://localhost:5433/test_db",
                "spring.datasource.username=test_user",
                "spring.datasource.password=test_password",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.clean-disabled=false"
        ).applyTo(context.getEnvironment());
    }
}
