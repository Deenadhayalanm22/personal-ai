package com.apps.deen_sa.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class MoneyStoryAggregateRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public MoneyStoryAggregateRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CategoryDay> categories(long userId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT aggregate_date, category, subcategory, spending_nature, total_amount, transaction_count,
                       updated_at FROM expense_daily_aggregate
                WHERE user_id=:userId AND aggregate_date >= :start AND aggregate_date < :end
                """, Map.of("userId", userId, "start", start, "end", end),
                (rs, n) -> category(rs));
    }

    public List<ReferenceDay> references(long userId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT edra.aggregate_date, edra.reference_entity_id, ure.canonical_name, edra.reference_type,
                       edra.total_amount, edra.transaction_count, edra.updated_at
                FROM expense_daily_reference_aggregate edra
                JOIN user_reference_entity ure ON ure.id=edra.reference_entity_id
                WHERE edra.user_id=:userId AND edra.aggregate_date >= :start AND edra.aggregate_date < :end
                  AND edra.reference_type='MERCHANT'
                """, Map.of("userId", userId, "start", start, "end", end), (rs, n) -> new ReferenceDay(
                rs.getObject("aggregate_date", LocalDate.class), rs.getLong("reference_entity_id"),
                rs.getString("canonical_name"), rs.getString("reference_type"), rs.getBigDecimal("total_amount"),
                rs.getInt("transaction_count"), timestamp(rs, "updated_at")));
    }

    /** Months with aggregated expense activity that have no current ready snapshot, plus stale snapshots. */
    public List<StoryMonth> monthsNeedingGeneration(int limit, Instant now) {
        return jdbc.query("""
                SELECT candidates.user_id, candidates.scope_month
                FROM (
                    SELECT DISTINCT user_id, date_trunc('month', occurred_at)::date AS scope_month
                    FROM financial_transaction WHERE deleted_at IS NULL
                    UNION
                    SELECT user_id, scope_month FROM money_story_snapshot
                    WHERE superseded_at IS NULL AND status = 'STALE'
                ) candidates
                LEFT JOIN money_story_snapshot ready ON ready.user_id=candidates.user_id
                    AND ready.scope_month=candidates.scope_month AND ready.superseded_at IS NULL
                    AND ready.status='READY'
                JOIN app_user owner ON owner.id=candidates.user_id
                WHERE ready.id IS NULL OR ready.evaluated_on IS NULL OR ready.calculation_version < 3
                   OR ready.evaluated_on <> (CAST(:now AS timestamptz) AT TIME ZONE owner.timezone)::date
                   OR EXISTS (
                    SELECT 1 FROM expense_daily_aggregate changed
                    WHERE changed.user_id=candidates.user_id
                      AND date_trunc('month', changed.aggregate_date)::date=candidates.scope_month
                      AND changed.updated_at > ready.input_watermark
                )
                   OR EXISTS (
                    SELECT 1 FROM financial_transaction changed
                    WHERE changed.user_id=candidates.user_id
                      AND date_trunc('month', changed.occurred_at)::date=candidates.scope_month
                      AND changed.updated_at > ready.input_watermark
                )
                ORDER BY candidates.scope_month, candidates.user_id
                LIMIT :limit
                """, Map.of("limit", limit, "now", java.sql.Timestamp.from(now)), (rs, n) -> new StoryMonth(rs.getLong("user_id"),
                rs.getObject("scope_month", LocalDate.class)));
    }

    private CategoryDay category(ResultSet rs) throws java.sql.SQLException {
        return new CategoryDay(rs.getObject("aggregate_date", LocalDate.class), rs.getString("category"),
                rs.getString("subcategory"), rs.getString("spending_nature"), rs.getBigDecimal("total_amount"),
                rs.getInt("transaction_count"), timestamp(rs, "updated_at"));
    }
    private Instant timestamp(ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    public record CategoryDay(LocalDate date, String category, String subcategory, String nature,
                              BigDecimal amount, int count, Instant updatedAt) { }
    public record ReferenceDay(LocalDate date, long referenceId, String referenceName, String referenceType,
                               BigDecimal amount, int count, Instant updatedAt) { }
    public record StoryMonth(long userId, LocalDate scopeMonth) { }
}
