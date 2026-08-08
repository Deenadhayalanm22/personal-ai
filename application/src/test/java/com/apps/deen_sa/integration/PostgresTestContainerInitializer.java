package com.apps.deen_sa.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.Properties;

@Slf4j
public class PostgresTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        Properties testProperties = loadTestProperties();

        TestPropertyValues.of(
                        "spring.datasource.url=" + testProperties.getProperty("spring.datasource.url"),
                        "spring.datasource.username=" + testProperties.getProperty("spring.datasource.username"),
                        "spring.datasource.password=" + testProperties.getProperty("spring.datasource.password"),
                        "spring.jpa.hibernate.ddl-auto=" + testProperties.getProperty("spring.jpa.hibernate.ddl-auto"),
                        "spring.jpa.properties.hibernate.dialect=" + testProperties.getProperty("spring.jpa.properties.hibernate.dialect"),
                        "spring.flyway.enabled=" + testProperties.getProperty("spring.flyway.enabled"),
                        "spring.flyway.clean-disabled=" + testProperties.getProperty("spring.flyway.clean-disabled"),
                        "spring.flyway.clean-on-validation-error=" + testProperties.getProperty("spring.flyway.clean-on-validation-error"))
                .applyTo(environment);

        log.info("[TEST CONFIG] Using Postgres at {} with user {}", testProperties.getProperty("spring.datasource.url"), testProperties.getProperty("spring.datasource.username"));
    }

    private Properties loadTestProperties() {
        try {
            return PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-test.properties"));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load application-test.properties for Postgres test configuration", exception);
        }
    }
}
