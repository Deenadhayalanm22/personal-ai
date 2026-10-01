package com.apps.deen_sa.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CommitmentPaymentMigrationIT {
    private final String url = System.getProperty("payment.test.url", "jdbc:postgresql://localhost:5433/test_db");
    private final String user = System.getProperty("payment.test.username", "test_user");
    private final String password = System.getProperty("payment.test.password", "test_password");
    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(false);
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load();
    }
    private void seed(String schema) throws Exception {
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) {
            c.setSchema(schema);
            s.execute("INSERT INTO app_user(id, channel, external_user_id) VALUES(9001, 'WHATSAPP', 'migration-payment')");
            s.execute("INSERT INTO user_recurring_commitment(id,user_id,label,amount_mode,planning_amount,effective_month,status,created_at,updated_at,next_expected_date,first_expected_date) VALUES (9101,9001,'Rent','FIXED',1000,'2026-10-01','ACTIVE',now(),now(),'2026-11-01','2026-10-01'), (9102,9001,'Internet','FIXED',799,'2026-10-01','ACTIVE',now(),now(),'2026-11-01','2026-10-01')");
            s.execute("INSERT INTO recurring_commitment_occurrence(id,commitment_id,scheduled_month,status,completed_at,actual_amount,extra_amount,created_at,updated_at) VALUES (7201,9101,'2026-10-01','COMPLETED','2026-10-01',900,200,now(),now()),(7202,9101,'2026-09-01','COMPLETED','2026-09-01',null,null,now(),now()),(7203,9102,'2026-10-01','COMPLETED','2026-10-01',799,null,now(),now())");
            s.execute("INSERT INTO recurring_commitment_extra(id,occurrence_id,amount,reason,created_at) VALUES(7601,7201,200,'Extra rent','2026-10-01T00:00:00Z')");
            s.execute("INSERT INTO transaction_draft(id,user_id,input_type,source,source_message_id,status) VALUES(7401,9001,'TEXT','WHATSAPP','payment-1','CONSUMED'),(7402,9001,'TEXT','WHATSAPP','payment-2','CONSUMED')");
            s.execute("INSERT INTO financial_transaction(id,user_id,amount,occurred_at,source_draft_id,recurring_commitment_id,commitment_match_status) VALUES(7301,9001,900,'2026-10-01',7401,9101,'MATCHED'),(7302,9001,200,'2026-10-01',7402,9101,'MATCHED')");
        }
    }
    @Test void reusesVerifiedExistingExpensesAndBackfillsMissingPaymentsWithoutInventingAcknowledgements() throws Exception {
        String schema = "payment_upgrade_" + UUID.randomUUID().toString().replace("-", ""); var upgrade = flyway(schema, null);
        try {
            flyway(schema, "36").migrate(); seed(schema); upgrade.migrate(); upgrade.validate();
            try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) {
                c.setSchema(schema);
                try (var rows = s.executeQuery("SELECT COUNT(*),SUM(amount) FROM financial_transaction WHERE user_id=9001")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(3); assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("1899");
                }
                try (var rows = s.executeQuery("SELECT payment_transaction_id FROM recurring_commitment_occurrence WHERE id=7201")) { rows.next(); assertThat(rows.getLong(1)).isEqualTo(7301); }
                try (var rows = s.executeQuery("SELECT payment_transaction_id FROM recurring_commitment_occurrence WHERE id=7202")) { rows.next(); assertThat(rows.getObject(1)).isNull(); }
                try (var rows = s.executeQuery("SELECT payment_transaction_id FROM recurring_commitment_extra WHERE id=7601")) { rows.next(); assertThat(rows.getLong(1)).isEqualTo(7302); }
            }
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
        } finally { upgrade.clean(); }
    }
    @Test void undatedAggregateExtrasRequireReconciliationInsteadOfAnInventedDate() throws Exception {
        String schema = "payment_undated_" + UUID.randomUUID().toString().replace("-", ""); var upgrade = flyway(schema, null);
        try {
            flyway(schema, "36").migrate(); seed(schema);
            try (var c = DriverManager.getConnection(url, user, password); var statement = c.createStatement()) {
                c.setSchema(schema); statement.execute("UPDATE recurring_commitment_occurrence SET extra_amount=300 WHERE id=7201");
            }
            assertThatThrownBy(upgrade::migrate).hasMessageContaining("Reconcile undated legacy commitment extras");
        } finally { upgrade.clean(); }
    }
    @Test void ambiguousExistingPaymentsStopTheUpgradeAtomically() throws Exception {
        String schema = "payment_ambiguous_" + UUID.randomUUID().toString().replace("-", ""); var upgrade = flyway(schema, null);
        try {
            flyway(schema, "36").migrate(); seed(schema);
            try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) {
                c.setSchema(schema);
                s.execute("INSERT INTO transaction_draft(id,user_id,input_type,source,source_message_id,status) VALUES(7403,9001,'TEXT','WHATSAPP','duplicate-1','CONSUMED')");
                s.execute("INSERT INTO financial_transaction(user_id,amount,occurred_at,source_draft_id,recurring_commitment_id,commitment_match_status) VALUES(9001,900,'2026-10-01',7403,9101,'MATCHED')");
            }
            assertThatThrownBy(upgrade::migrate).hasMessageContaining("Reconcile commitment occurrence 7201");
            try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement(); var rows = s.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='financial_transaction' AND column_name='origin'")) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
        } finally { upgrade.clean(); }
    }
}
