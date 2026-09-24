package com.apps.deen_sa.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayUpgradeIT {
    @TempDir
    Path previousRelease;

    private final String url = System.getProperty("migration.test.url", "jdbc:postgresql://localhost:5433/test_db");
    private final String username = System.getProperty("migration.test.username", "test_user");
    private final String password = System.getProperty("migration.test.password", "test_password");
    private final String schema = "migration_upgrade_" + UUID.randomUUID().toString().replace("-", "");

    @Test
    void appliesLateV22AfterV28AndCanRestart() throws Exception {
        var scripts = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql");
        for (var script : scripts) {
            if (!script.getFilename().startsWith("V22__")) {
                try (var input = script.getInputStream()) {
                    Files.copy(input, previousRelease.resolve(script.getFilename()));
                }
            }
        }

        Flyway upgrade = flyway("classpath:db/migration", applicationOutOfOrder());
        try {
            Flyway previous = flyway("filesystem:" + previousRelease, false);
            previous.migrate();
            assertThat(previous.info().current().getVersion().getVersion()).isEqualTo("28");
            assertThatThrownBy(() -> flyway("classpath:db/migration", false).migrate())
                    .isInstanceOf(FlywayValidateException.class)
                    .hasMessageContaining("Detected resolved migration not applied to database: 22");

            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertCommitmentExtraSchema();
            upgrade.validate();
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
        } finally {
            upgrade.clean();
        }
    }

    @Test
    void migratesFreshDatabaseWithApplicationOrdering() throws Exception {
        Flyway fresh = flyway("classpath:db/migration", applicationOutOfOrder());
        try {
            fresh.migrate();
            fresh.validate();
            assertCommitmentExtraSchema();
            assertThat(fresh.migrate().migrationsExecuted).isZero();
        } finally {
            fresh.clean();
        }
    }

    private boolean applicationOutOfOrder() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        return Boolean.parseBoolean(yaml.getObject().getProperty("spring.flyway.out-of-order", "false"));
    }

    private Flyway flyway(String location, boolean outOfOrder) {
        return Flyway.configure().dataSource(url, username, password)
                .schemas(schema).locations(location).outOfOrder(outOfOrder)
                .cleanDisabled(false).load();
    }

    private void assertCommitmentExtraSchema() throws Exception {
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            // Query the actual objects required by the application, even when the tables are empty.
            statement.executeQuery("SELECT extra_amount FROM " + schema + ".recurring_commitment_occurrence LIMIT 0").close();
            statement.executeQuery("SELECT occurrence_id, amount, reason, created_at FROM " + schema
                    + ".recurring_commitment_extra LIMIT 0").close();
        }
    }
}
