package com.apps.deen_sa.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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

    @Test
    void movesExistingPortalAccessToAppUserWithoutChangingUserId() throws Exception {
        Flyway initial = Flyway.configure().dataSource(url, username, password)
                .schemas(schema).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1")).cleanDisabled(false).load();
        Flyway upgrade = flyway("classpath:db/migration", applicationOutOfOrder());
        try {
            initial.migrate();
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO " + schema + ".app_user (id, channel, external_user_id) "
                        + "VALUES (9001, 'WHATSAPP', '919876543210')");
                statement.execute("INSERT INTO " + schema + ".user_feature_flag "
                        + "(channel, external_user_id, role, enabled) VALUES "
                        + "('WHATSAPP', '919876543210', 'USER', TRUE), "
                        + "('WHATSAPP', '919876543211', 'USER', FALSE)");
            }
            upgrade.migrate();
            try (var connection = DriverManager.getConnection(url, username, password);
                 var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT id, portal_enabled, role FROM " + schema
                        + ".app_user WHERE external_user_id = '919876543210'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isEqualTo(9001);
                    assertThat(rows.getBoolean("portal_enabled")).isTrue();
                    assertThat(rows.getString("role")).isEqualTo("USER");
                }
                try (var rows = statement.executeQuery("SELECT portal_enabled FROM " + schema
                        + ".app_user WHERE external_user_id = '919876543211'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isFalse();
                }
                try (var rows = statement.executeQuery("SELECT role, portal_enabled FROM " + schema
                        + ".app_user WHERE external_user_id = '919004656025'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("role")).isEqualTo("SUPER_ADMIN");
                    assertThat(rows.getBoolean("portal_enabled")).isTrue();
                }
            }
        } finally {
            upgrade.clean();
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
