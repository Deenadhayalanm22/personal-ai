package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** FIN-EPIC-003/005/006: one read-only timeline over separately owned financial records. */
@Service
@RequiredArgsConstructor
public class FinancialActivityService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public ActivityResponse list(AppUserEntity user, YearMonth month) {
        LocalDate start = month.atDay(1), end = month.plusMonths(1).atDay(1);
        List<ActivityItem> expenses = rows("""
            SELECT t.id, t.occurred_at AS activity_date, t.amount,
                   COALESCE(t.description, m.canonical_name, t.category, 'Expense') AS label,
                   CASE WHEN EXISTS (SELECT 1 FROM user_credit_card c
                       WHERE c.user_id = t.user_id AND c.account_reference_id = t.source_account_id)
                       THEN 'On credit card · bill paid separately' ELSE 'Recorded expense' END AS activity_description
              FROM financial_transaction t
              LEFT JOIN user_reference_entity m ON m.id = t.merchant_id
             WHERE t.user_id = ? AND t.deleted_at IS NULL
               AND t.occurred_at >= ? AND t.occurred_at < ?
               AND NOT EXISTS (SELECT 1 FROM recurring_commitment_occurrence o WHERE o.payment_transaction_id = t.id)
               AND NOT EXISTS (SELECT 1 FROM recurring_commitment_extra e WHERE e.payment_transaction_id = t.id)
            """, user.getId(), start, end, "EXPENSE", "Recorded expense");
        List<ActivityItem> commitments = rows("""
            SELECT t.id, t.occurred_at AS activity_date, t.amount,
                   COALESCE(t.description, c.label) AS label
              FROM financial_transaction t
              LEFT JOIN recurring_commitment_occurrence o ON o.payment_transaction_id = t.id
              LEFT JOIN recurring_commitment_extra e ON e.payment_transaction_id = t.id
              JOIN user_recurring_commitment c ON c.id = COALESCE(o.commitment_id,
                  (SELECT parent.commitment_id FROM recurring_commitment_occurrence parent WHERE parent.id = e.occurrence_id))
             WHERE t.user_id = ? AND t.deleted_at IS NULL AND c.user_id = t.user_id
               AND t.occurred_at >= ? AND t.occurred_at < ?
            """, user.getId(), start, end, "COMMITMENT", "Commitment paid");
        List<ActivityItem> loans = rows("""
            SELECT o.id, o.paid_at AS activity_date, o.paid_amount AS amount, l.loan_name AS label
              FROM loan_emi_occurrence o JOIN user_loan l ON l.id = o.loan_id
             WHERE l.user_id = ? AND o.status = 'PAID'
               AND o.paid_at >= ? AND o.paid_at < ?
            """, user.getId(), start, end, "LOAN", "Loan payment");
        List<ActivityItem> investments = rows("""
            SELECT t.id, t.transaction_date AS activity_date, t.amount,
                   i.display_name_snapshot AS label
              FROM investment_transaction t JOIN user_investment i ON i.id = t.user_investment_id
             WHERE i.user_id = ? AND t.status = 'CONFIRMED'
               AND t.transaction_kind <> 'OPENING_BALANCE'
               AND t.transaction_date >= ? AND t.transaction_date < ?
            """, user.getId(), start, end, "INVESTMENT", "Investment recorded");
        List<ActivityItem> savings = rows("""
            SELECT e.id, e.recorded_at AS activity_date, e.amount, c.label
              FROM commitment_savings_entry e
              JOIN commitment_savings_plan p ON p.id = e.plan_id
              JOIN user_recurring_commitment c ON c.id = p.commitment_id
             WHERE c.user_id = ? AND e.status = 'SAVED'
               AND e.recorded_at >= ? AND e.recorded_at < ?
            """, user.getId(), start, end, "SAVINGS", "Savings set aside");
        List<ActivityItem> cardPayments = rows("""
            SELECT p.id, p.paid_at AS activity_date, p.amount, c.card_name AS label
              FROM credit_card_bill_payment p JOIN user_credit_card c ON c.id = p.card_id
             WHERE c.user_id = ? AND p.paid_at >= ? AND p.paid_at < ?
            """, user.getId(), start, end, "CARD_PAYMENT", "Card bill payment · purchases already counted in spending");
        List<ActivityItem> items = Stream.of(expenses, commitments, loans, investments, savings, cardPayments)
                .flatMap(List::stream)
                .sorted(Comparator.comparing(ActivityItem::date).reversed()
                        .thenComparing(ActivityItem::type).thenComparing(ActivityItem::id, Comparator.reverseOrder()))
                .toList();
        return new ActivityResponse(month.toString(), user.getCurrency(), items);
    }

    private List<ActivityItem> rows(String sql, Long userId, LocalDate start, LocalDate end,
                                    String type, String description) {
        return jdbc.query(sql, (rs, row) -> new ActivityItem(type, rs.getLong("id"),
                rs.getDate("activity_date").toLocalDate(), rs.getBigDecimal("amount"),
                rs.getString("label"), type.equals("EXPENSE") ? rs.getString("activity_description") : description), userId, Date.valueOf(start), Date.valueOf(end));
    }

    public record ActivityItem(String type, Long id, LocalDate date, BigDecimal amount,
                               String label, String description) {}
    public record ActivityResponse(String month, String currency, List<ActivityItem> items) {}
}
