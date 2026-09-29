package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.exception.WebApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** FIN-EPIC-003: read-only conversational expense exploration. */
@Service
public class ExpenseQueryTool {
    private static final Map<String, String> FIELDS = Map.ofEntries(
            Map.entry("category", "coalesce(t.category, 'Uncategorized')"),
            Map.entry("subcategory", "coalesce(t.subcategory, 'Uncategorized')"),
            Map.entry("merchant", "coalesce(m.canonical_name, 'Unknown')"),
            Map.entry("account", "coalesce(a.canonical_name, 'Unknown')"),
            Map.entry("nature", "coalesce(t.spending_nature, 'UNKNOWN')"),
            Map.entry("day", "cast(t.occurred_at as text)"),
            Map.entry("month", "to_char(t.occurred_at, 'YYYY-MM')"),
            Map.entry("weekday", "cast(extract(isodow from t.occurred_at) as text)"));
    private static final String FROM = """
             FROM financial_transaction t
             LEFT JOIN user_reference_entity m ON m.id = t.merchant_id AND m.user_id = t.user_id
             LEFT JOIN user_reference_entity a ON a.id = t.source_account_id AND a.user_id = t.user_id
             WHERE t.user_id = :owner AND t.deleted_at IS NULL
               AND t.occurred_at >= :start AND t.occurred_at < :end
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public ExpenseQueryTool(DataSource dataSource) {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().setQueryTimeout(5);
    }

    @Transactional(readOnly = true, timeout = 10, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Result execute(AppUserEntity user, Query query) {
        Plan plan = plan(user.getId(), query);
        Map<String, Object> summary = jdbc.queryForMap(
                "SELECT count(*) AS count, coalesce(sum(t.amount), 0) AS total" + plan.where(), plan.parameters());
        List<Map<String, Object>> fetched = jdbc.queryForList(plan.sql(), plan.parameters());
        boolean truncated = fetched.size() > query.limit();
        List<Map<String, Object>> rows = truncated ? fetched.subList(0, query.limit()) : fetched;
        return new Result(query, user.getCurrency(), ((Number) summary.get("count")).longValue(),
                (BigDecimal) summary.get("total"), List.copyOf(rows), truncated);
    }

    // Every identifier and operator comes from code; every value is bound. No caller SQL or owner is accepted.
    static Plan plan(Long owner, Query query) {
        if (query == null || query.startDate() == null || query.endDate() == null
                || !query.endDate().isAfter(query.startDate())
                || ChronoUnit.DAYS.between(query.startDate(), query.endDate()) > 366
                || query.limit() < 1 || query.limit() > 50
                || query.filters() == null || query.filters().size() > 8
                || query.groupBy() == null || query.groupBy().size() > 2
                || new HashSet<>(query.groupBy()).size() != query.groupBy().size()
                || !Set.of("summary", "details").contains(Objects.toString(query.mode(), ""))
                || !Set.of("amount_desc", "date_desc", "label_asc").contains(Objects.toString(query.orderBy(), ""))) {
            throw invalid("Use a date range of 1–366 days, up to two groups/eight filters, and a limit of 1–50.");
        }
        List<String> groups = query.groupBy().stream().map(ExpenseQueryTool::field).toList();
        if (query.mode().equals("details") && !groups.isEmpty()) throw invalid("Details cannot be grouped.");
        var parameters = new MapSqlParameterSource("owner", owner)
                .addValue("start", java.sql.Date.valueOf(query.startDate()))
                .addValue("end", java.sql.Date.valueOf(query.endDate()))
                .addValue("limit", query.limit() + 1);
        StringBuilder where = new StringBuilder(FROM);
        for (int i = 0; i < query.filters().size(); i++) {
            Filter filter = query.filters().get(i);
            if (filter == null || filter.value() == null || filter.value().isBlank() || filter.value().length() > 150)
                throw invalid("Filters require a value of at most 150 characters.");
            String parameter = "f" + i;
            if ("amount".equals(filter.field())) {
                String op = switch (Objects.toString(filter.operator(), "")) {
                    case "eq" -> "="; case "ne" -> "<>"; case "gte" -> ">="; case "lte" -> "<=";
                    default -> throw invalid("Amount supports eq, ne, gte and lte.");
                };
                try { parameters.addValue(parameter, new BigDecimal(filter.value())); }
                catch (NumberFormatException ex) { throw invalid("Amount must be numeric."); }
                where.append(" AND t.amount ").append(op).append(" :").append(parameter);
            } else {
                String column = field(filter.field());
                String op = switch (Objects.toString(filter.operator(), "")) {
                    case "eq" -> "="; case "ne" -> "<>"; case "contains" -> "LIKE";
                    default -> throw invalid("Text filters support eq, ne and contains.");
                };
                String value = filter.value().toLowerCase(Locale.ROOT);
                if (op.equals("LIKE")) value = "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                parameters.addValue(parameter, value);
                where.append(" AND lower(").append(column).append(") ").append(op).append(" :").append(parameter);
                if (op.equals("LIKE")) where.append(" ESCAPE '!'");
            }
        }
        String sql;
        if (query.mode().equals("details")) {
            sql = "SELECT t.id, cast(t.occurred_at as text) AS day, t.amount, "
                    + FIELDS.get("category") + " AS category, " + FIELDS.get("subcategory") + " AS subcategory, "
                    + FIELDS.get("merchant") + " AS merchant, " + FIELDS.get("account") + " AS account"
                    + where + (query.orderBy().equals("amount_desc") ? " ORDER BY t.amount DESC, t.id DESC" : query.orderBy().equals("label_asc") ? " ORDER BY category ASC, t.id DESC" : " ORDER BY t.occurred_at DESC, t.id DESC");
        } else {
            String dimensions = "";
            for (int i = 0; i < groups.size(); i++) dimensions += groups.get(i) + " AS " + query.groupBy().get(i) + ", ";
            sql = "SELECT " + dimensions + "count(*) AS count, coalesce(sum(t.amount), 0) AS total, "
                    + "coalesce(avg(t.amount), 0) AS average, coalesce(max(t.amount), 0) AS largest" + where;
            if (!groups.isEmpty()) {
                sql += " GROUP BY " + String.join(", ", groups);
                sql += query.orderBy().equals("amount_desc") ? " ORDER BY total DESC, " + String.join(", ", groups)
                        : " ORDER BY " + String.join(query.orderBy().equals("date_desc") ? " DESC, " : ", ", groups) + (query.orderBy().equals("date_desc") ? " DESC" : "");
            }
        }
        return new Plan(sql + " LIMIT :limit", where.toString(), parameters);
    }

    private static String field(String name) {
        String value = name == null ? null : FIELDS.get(name);
        if (value == null) throw invalid("Unknown expense field.");
        return value;
    }
    static WebApiException invalid(String message) {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_EXPENSE_QUERY", message);
    }
    record Plan(String sql, String where, MapSqlParameterSource parameters) {}
    public record Filter(String field, String operator, String value) {}
    public record Query(LocalDate startDate, LocalDate endDate, String mode, List<String> groupBy,
                        List<Filter> filters, String orderBy, int limit) {}
    public record Result(Query query, String currency, long matchingCount, BigDecimal matchingTotal,
                         List<Map<String, Object>> rows, boolean truncated) {}
}
