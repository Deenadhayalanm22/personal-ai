package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.util.*;

/** FIN-EPIC-005/006: bounded, read-only module records and payment histories. */
@Service
public class FinancialRecordsTool {
    private final NamedParameterJdbcTemplate jdbc;
    public FinancialRecordsTool(DataSource dataSource) {
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().setQueryTimeout(5);
    }
    @Transactional(readOnly = true, timeout = 10, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Result read(AppUserEntity user, Request request) {
        if (request == null || request.module() == null || request.view() == null || request.limit() < 1 || request.limit() > 50
                || request.offset() < 0 || request.offset() > 10000 || (request.id() != null && request.id() <= 0)
                || request.search() == null || request.search().length() > 100
                || !Set.of("records", "history").contains(request.view())) throw ExpenseQueryTool.invalid("Invalid module request or pagination.");
        boolean history = request.view().equals("history");
        if (history && request.id() == null) throw ExpenseQueryTool.invalid("Choose a record ID before reading its history.");
        Dataset dataset = dataset(request.module(), history);
        String where = " WHERE " + dataset.owner() + " = :owner" + dataset.extra();
        var params = new MapSqlParameterSource("owner", user.getId()).addValue("limit", request.limit() + 1).addValue("offset", request.offset());
        if (request.id() != null) { where += " AND " + dataset.id() + " = :id"; params.addValue("id", request.id()); }
        if (!request.search().isBlank()) {
            where += " AND lower(" + dataset.label() + ") LIKE :search ESCAPE '!'";
            params.addValue("search", "%" + request.search().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long count = jdbc.queryForObject("SELECT count(*) " + dataset.from() + where, params, Long.class);
        var fetched = jdbc.queryForList("SELECT " + dataset.columns() + " " + dataset.from() + where
                + " ORDER BY " + dataset.order() + " LIMIT :limit OFFSET :offset", params);
        boolean truncated = fetched.size() > request.limit();
        return new Result("records", request.module(), request.view(), user.getCurrency(), count,
                List.copyOf(truncated ? fetched.subList(0, request.limit()) : fetched), truncated,
                truncated ? request.offset() + request.limit() : null, dataset.note());
    }
    private Dataset dataset(String module, boolean history) {
        if (Set.of("mutual_funds", "stocks").contains(module)) {
            String extra = " AND i.asset_type = '" + (module.equals("stocks") ? "STOCK" : "MUTUAL_FUND") + "'";
            if (history) return new Dataset("x.id, i.display_name_snapshot AS name, x.transaction_kind, x.status, x.scheduled_month, x.transaction_date, x.amount, x.units, x.unit_price",
                    "FROM investment_transaction x JOIN user_investment i ON i.id = x.user_investment_id", "i.user_id", "i.id", "i.display_name_snapshot", extra, "x.id DESC",
                    "Only CONFIRMED rows are actual investments. Scheduled or skipped amounts are not paid.");
            return new Dataset("i.id, i.display_name_snapshot AS name, i.external_instrument_id AS instrument, i.exchange, i.sip_amount AS planned_contribution, i.sip_day, i.sip_start_month, i.sip_status, i.sip_frequency, i.sip_anchor_month, "
                    + "coalesce((SELECT sum(x.amount) FROM investment_transaction x WHERE x.user_investment_id=i.id AND x.status='CONFIRMED'),0) AS invested_amount, "
                    + "coalesce((SELECT sum(x.units) FROM investment_transaction x WHERE x.user_investment_id=i.id AND x.status='CONFIRMED'),0) AS units",
                    "FROM user_investment i", "i.user_id", "i.id", "i.display_name_snapshot", extra, "i.id DESC",
                    "Recorded holdings and plans only. No live market prices, current valuation, sale proceeds or available cash. Invested amounts/units include confirmed records only.");
        }
        return switch (module) {
            case "loans" -> history
                    ? new Dataset("x.id, l.loan_name AS name, x.due_date, x.status, x.planned_amount, x.paid_amount, x.paid_at, x.bank_penalty_amount, x.pre_closure_settlement", "FROM loan_emi_occurrence x JOIN user_loan l ON l.id=x.loan_id", "l.user_id", "l.id", "l.loan_name", "", "x.due_date DESC, x.id DESC", "Recorded outcomes only. Absence of a row is not proof of payment.")
                    : new Dataset("l.id, l.loan_name AS name, l.loan_type, l.lender_name, l.original_principal, l.monthly_emi_amount, l.total_tenure_months, l.first_emi_due_date, l.status", "FROM user_loan l", "l.user_id", "l.id", "l.loan_name", "", "l.id DESC", "Original principal is not outstanding balance. Skipping in the application is not lender permission.");
            case "commitments" -> history
                    ? new Dataset("x.id, c.label AS name, x.scheduled_month, x.status, x.completed_at, x.actual_amount, x.extra_amount, x.savings_used", "FROM recurring_commitment_occurrence x JOIN user_recurring_commitment c ON c.id=x.commitment_id", "c.user_id", "c.id", "c.label", "", "x.scheduled_month DESC, x.id DESC", "Acknowledged outcomes; savings_used is an allocation, not another payment.")
                    : new Dataset("c.id, c.label AS name, c.planning_amount, c.amount_mode, c.status, c.category, c.subcategory, c.recurrence_unit, c.recurrence_interval, c.first_expected_date, c.next_expected_date, c.flexible_schedule, c.effective_month", "FROM user_recurring_commitment c", "c.user_id", "c.id", "c.label", "", "c.id DESC", "Amounts are per occurrence; use read_monthly_plan for month totals. Flexible schedule does not establish that a payment is optional.");
            case "savings" -> history
                    ? new Dataset("x.id, c.label AS name, x.scheduled_month, x.status, x.amount, x.recorded_at", "FROM commitment_savings_entry x JOIN commitment_savings_plan p ON p.id=x.plan_id JOIN user_recurring_commitment c ON c.id=p.commitment_id", "c.user_id", "p.id", "c.label", "", "x.scheduled_month DESC, x.id DESC", "SAVED means recorded money set aside, not a bill payment or verified bank balance.")
                    : new Dataset("p.id, c.label AS name, p.target_date, p.target_amount, p.monthly_amount, p.final_amount, p.start_month, p.used_amount, coalesce((SELECT sum(x.amount) FROM commitment_savings_entry x WHERE x.plan_id=p.id AND x.status='SAVED'),0) AS recorded_saved", "FROM commitment_savings_plan p JOIN user_recurring_commitment c ON c.id=p.commitment_id", "c.user_id", "p.id", "c.label", "", "p.id DESC", "Recorded savings are earmarked, not free cash. Deduct used_amount when describing unallocated savings; do not add savings contributions and bill payment twice.");
            case "credit_cards" -> {
                if (history) throw ExpenseQueryTool.invalid("Card payment history is unavailable. Query recorded expenses or the monthly bill projection.");
                yield new Dataset("c.id, c.card_name AS name, c.issuer_name, c.statement_day, c.due_day, c.active", "FROM user_credit_card c", "c.user_id", "c.id", "c.card_name", "", "c.id DESC", "Billing configuration only. Projected card bills use recorded card spending, not a bank statement or balance.");
            }
            case "accounts" -> {
                if (history) throw ExpenseQueryTool.invalid("Use query_expenses with an account filter for recorded expense history.");
                yield new Dataset("a.id, a.canonical_name AS name, a.active", "FROM user_reference_entity a", "a.user_id", "a.id", "a.canonical_name", " AND a.entity_type='ACCOUNT'", "a.id DESC", "Account labels only; no balances or bank reconciliation are available.");
            }
            default -> throw ExpenseQueryTool.invalid("Unknown module.");
        };
    }
    private record Dataset(String columns, String from, String owner, String id, String label, String extra, String order, String note) {}
    public record Request(String module, String view, Long id, String search, int limit, int offset) {}
    public record Result(String kind, String module, String view, String currency, long matchingCount,
                         List<Map<String,Object>> rows, boolean truncated, Integer nextOffset, String note) {}
}
