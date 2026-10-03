package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.exception.WebApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** FIN-022 / FIN-EPIC-005: bill settlements are separate from purchase expenses. */
@Service
@RequiredArgsConstructor
public class CreditCardBillService {
    private final UserCreditCardRepository cards;
    private final FinancialTransactionRepository transactions;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BillList list(AppUserEntity user, YearMonth month) {
        return new BillList(month.toString(), cards.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())
                .stream().filter(card -> includesMonth(card,month)).map(card -> bill(user, card, month)).toList());
    }

    /** The bill is generated on statementDay for purchases strictly before that day. */
    public static LocalDate statementEnd(UserCreditCardEntity card, YearMonth month) {
        return statementGeneratedAt(card, month).minusDays(1);
    }

    public static boolean includesMonth(UserCreditCardEntity card,YearMonth month) {
        return card.getStartMonth()==null || !month.isBefore(YearMonth.from(card.getStartMonth()));
    }

    public static LocalDate statementGeneratedAt(UserCreditCardEntity card, YearMonth month) {
        return (card.getDueDay() > card.getStatementDay() ? month : month.minusMonths(1)).atDay(card.getStatementDay());
    }

    public static LocalDate periodStart(UserCreditCardEntity card, YearMonth month) {
        return statementGeneratedAt(card, month).minusMonths(1);
    }

    @Transactional(readOnly = true)
    public BigDecimal paid(AppUserEntity user, Long cardId, YearMonth month) {
        var card = owned(user, cardId);
        return paidForStatement(cardId, statementEnd(card, month));
    }

    private BigDecimal paidForStatement(Long id, LocalDate end) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM credit_card_bill_payment WHERE card_id = ? AND statement_end = ?",
                BigDecimal.class, id, java.sql.Date.valueOf(end));
    }

    private Bill bill(AppUserEntity user, UserCreditCardEntity card, YearMonth month) {
        LocalDate end = statementEnd(card, month), start = periodStart(card, month);
        BigDecimal projected = includesMonth(card,month)?transactions.sumVisibleByAccountAndPeriod(user.getId(), card.getAccountReference().getId(), start, end.plusDays(1)):BigDecimal.ZERO;
        BigDecimal monthlyPurchases = transactions.sumVisibleByAccountAndPeriod(user.getId(), card.getAccountReference().getId(), month.atDay(1), month.plusMonths(1).atDay(1));
        BigDecimal paid = paidForStatement(card.getId(), end);
        var history = jdbc.query("SELECT id, paid_at, amount FROM credit_card_bill_payment WHERE card_id = ? AND statement_end = ? ORDER BY paid_at DESC, id DESC",
                (rs, row) -> new Payment(rs.getLong("id"), rs.getDate("paid_at").toLocalDate(), rs.getBigDecimal("amount")), card.getId(), java.sql.Date.valueOf(end));
        return new Bill(card.getId(), card.getCardName(), month.toString(), start, end, month.atDay(card.getDueDay()),
                projected, paid, projected.subtract(paid).max(BigDecimal.ZERO), end.isBefore(today(user)), history, end.plusDays(1), monthlyPurchases, paid.subtract(projected).max(BigDecimal.ZERO));
    }

    @Transactional
    public Bill record(AppUserEntity user, Long id, PaymentRequest request) {
        // Read under the same card lock used by billing-profile edits.
        var card = cards.findOwnedForUpdate(id, user.getId()).filter(UserCreditCardEntity::isActive)
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "CREDIT_CARD_NOT_FOUND", "Credit card not found"));
        if (request == null || request.requestId() == null || request.amount() == null || request.paidAt() == null || request.month() == null)
            throw invalid("Month, amount, payment date and request ID are required");
        YearMonth month;
        try { month = YearMonth.parse(request.month()); } catch (RuntimeException e) { throw invalid("Use a valid YYYY-MM bill month"); }
        BigDecimal amount = request.amount();
        if (amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 17 || request.paidAt().isAfter(today(user)))
            throw invalid("Enter a positive amount with at most two decimal places and a past or current payment date");
        LocalDate end = statementEnd(card, month);
        var previous = jdbc.query("SELECT due_month, paid_at, amount FROM credit_card_bill_payment WHERE card_id = ? AND request_id = ?",
                (rs, row) -> new Previous(rs.getDate("due_month").toLocalDate(), rs.getDate("paid_at").toLocalDate(), rs.getBigDecimal("amount")), id, request.requestId());
        if (!previous.isEmpty()) {
            var p = previous.getFirst();
            if (!p.month().equals(month.atDay(1)) || !p.date().equals(request.paidAt()) || p.amount().compareTo(amount) != 0)
                throw new WebApiException(HttpStatus.CONFLICT, "CARD_PAYMENT_REQUEST_CONFLICT", "This payment request was already used with different details");
            return bill(user, card, month);
        }
        if(!includesMonth(card,month))throw invalid("This bill is before the card’s start month");
        if (!end.isBefore(today(user)) || !request.paidAt().isAfter(end)) throw invalid("Record a payment on or after the bill generation date");
        jdbc.update("INSERT INTO credit_card_bill_payment(card_id, due_month, statement_end, paid_at, amount, request_id) VALUES (?, ?, ?, ?, ?, ?)",
                id, java.sql.Date.valueOf(month.atDay(1)), java.sql.Date.valueOf(end), java.sql.Date.valueOf(request.paidAt()), amount, request.requestId());
        return bill(user, card, month);
    }

    private UserCreditCardEntity owned(AppUserEntity user, Long id) {
        return cards.findByIdAndUserId(id, user.getId()).filter(UserCreditCardEntity::isActive)
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "CREDIT_CARD_NOT_FOUND", "Credit card not found"));
    }
    private LocalDate today(AppUserEntity user) { return LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone()))); }
    private WebApiException invalid(String message) { return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_CARD_PAYMENT", message); }
    private record Previous(LocalDate month, LocalDate date, BigDecimal amount) {}
    public record PaymentRequest(UUID requestId, String month, BigDecimal amount, LocalDate paidAt) {}
    public record Payment(Long id, LocalDate paidAt, BigDecimal amount) {}
    public record Bill(Long cardId, String cardName, String month, LocalDate periodStart, LocalDate statementEnd,
                       LocalDate dueDate, BigDecimal projectedAmount, BigDecimal paidAmount, BigDecimal remaining,
                       boolean statementClosed, List<Payment> payments, LocalDate statementGeneratedAt, BigDecimal monthlyPurchaseAmount, BigDecimal unmatchedPaymentAmount) {}
    public record BillList(String month, List<Bill> bills) {}
}
