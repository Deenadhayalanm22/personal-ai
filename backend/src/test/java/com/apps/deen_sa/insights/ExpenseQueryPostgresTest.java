package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Uses a unique schema, never cleans an existing application schema. */
@EnabledIfEnvironmentVariable(named = "EXPENSE_CHAT_TEST_DB_URL", matches = ".+")
class ExpenseQueryPostgresTest {
    static JdbcTemplate jdbc;
    static ExpenseQueryTool tool;
    static String schema;
    static AppUserEntity owner;
    @BeforeAll static void setup() {
        schema = "chat_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("EXPENSE_CHAT_TEST_DB_URL");
        var admin = new DriverManagerDataSource(url, System.getenv("EXPENSE_CHAT_TEST_DB_USER"), System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD"));
        Flyway.configure().dataSource(admin).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        var dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                System.getenv("EXPENSE_CHAT_TEST_DB_USER"), System.getenv("EXPENSE_CHAT_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource); tool = new ExpenseQueryTool(dataSource);
        owner = new AppUserEntity(); owner.setId(user("owner"));
        long other = user("other");
        expense(owner.getId(), "2026-09-01", "Food", "250", false);
        expense(owner.getId(), "2026-09-05", "Food", "500", false);
        expense(owner.getId(), "2026-09-06", "Rent", "2000", false);
        expense(owner.getId(), "2026-09-06", "Hidden", "9000", true);
        expense(owner.getId(), "2026-08-06", "Food", "100", false);
        expense(other, "2026-09-05", "Other user", "99000", false);
    }
    @AfterAll static void cleanup() { if (jdbc != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE"); }
    static long user(String name) {
        return jdbc.queryForObject("INSERT INTO app_user(channel,external_user_id) VALUES ('WHATSAPP',?) RETURNING id", Long.class, name);
    }
    static void expense(long user, String date, String category, String amount, boolean deleted) {
        long draft = jdbc.queryForObject("INSERT INTO transaction_draft(user_id,input_type,source,source_message_id,status) VALUES (?, 'TEXT','WHATSAPP',?,'CONSUMED') RETURNING id", Long.class, user, UUID.randomUUID().toString());
        jdbc.update("INSERT INTO financial_transaction(user_id,source_draft_id,occurred_at,category,amount,deleted_at) VALUES (?,?,cast(? as date),?,?,CASE WHEN ? THEN now() ELSE NULL END)", user, draft, date, category, new BigDecimal(amount), deleted);
    }
    ExpenseQueryTool.Query query(String mode, List<String> groups, List<ExpenseQueryTool.Filter> filters, int limit) {
        return new ExpenseQueryTool.Query(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"), mode, groups, filters, "amount_desc", limit);
    }
    @Test void aggregatesOnlyOwnedVisiblePeriodAndPreservesTotalsWhenTruncated() {
        var result = tool.execute(owner, query("summary", List.of("category"), List.of(), 1));
        assertThat(result.matchingTotal()).isEqualByComparingTo("2750");
        assertThat(result.matchingCount()).isEqualTo(3);
        assertThat(result.truncated()).isTrue();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().get("category")).isEqualTo("Rent");
    }
    @Test void combinesFiltersAndGroupingForAnUnscriptedQuestion() {
        var result = tool.execute(owner, query("summary", List.of("category", "weekday"), List.of(
                new ExpenseQueryTool.Filter("category", "ne", "rent"), new ExpenseQueryTool.Filter("amount", "gte", "300")), 20));
        assertThat(result.matchingTotal()).isEqualByComparingTo("500");
        assertThat(result.rows().getFirst()).containsEntry("weekday", "6");
    }
    @Test void detailLimitDoesNotChangeTotalAndEmptyQueryHasNoInventedRows() {
        var result = tool.execute(owner, query("details", List.of(), List.of(), 2));
        assertThat(result.rows()).hasSize(2);
        assertThat(result.matchingTotal()).isEqualByComparingTo("2750");
        var empty = tool.execute(owner, query("summary", List.of("category"), List.of(new ExpenseQueryTool.Filter("category", "contains", "%' OR 1=1 --")), 20));
        assertThat(empty.rows()).isEmpty(); assertThat(empty.matchingCount()).isZero();
    }
}
