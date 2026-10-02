package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

/** FIN-EPIC-003/005/006: unpaid plan sources, never planned minus actual spending. */
@Service
@RequiredArgsConstructor
public class MonthlyPaymentOverviewService {
    private final MonthlyFinancialSnapshotService snapshots;
    private final RecurringCommitmentOccurrenceRepository occurrences;
    private final LoanEmiOccurrenceRepository loans;
    private final InvestmentTransactionRepository investments;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired(required = false) CreditCardBillService cardBills;

    @Transactional(readOnly = true)
    public Overview forMonth(AppUserEntity user, YearMonth month) {
        YearMonth current = YearMonth.now(clock.withZone(java.time.ZoneId.of(user.getTimezone())));
        if (!month.equals(current) && !month.equals(current.plusMonths(1))) return null;
        var snapshot = snapshots.preview(user, month);
        var recurringOutcomes = new java.util.HashMap<Long, java.util.List<com.apps.deen_sa.entity.RecurringCommitmentOccurrenceEntity>>();
        BigDecimal bills = BigDecimal.ZERO, investing = BigDecimal.ZERO, savings = BigDecimal.ZERO;
        for (var bucket : snapshot.commitmentBuckets()) for (var source : bucket.sources()) {
            Long id = Long.valueOf(source.sourceId());
            boolean unpaid = switch (source.sourceType()) {
                case "RECURRING_COMMITMENT" -> recurringOutcomes.computeIfAbsent(id, key -> occurrences.findByCommitmentIdAndScheduledMonthBetweenOrderByScheduledMonthAsc(key, month.atDay(1), month.atEndOfMonth()))
                        .stream().noneMatch(o -> (RecurringCommitmentSchedule.dated(o.getCommitment()) ? o.getScheduledMonth().equals(source.dueDate()) : o.getScheduledMonth().equals(month.atDay(1)))
                                && o.getStatus() != RecurringCommitmentOccurrenceStatus.DUE);
                case "LOAN" -> loans.findByLoanIdAndDueMonth(id, month.atDay(1))
                        .map(o -> o.getStatus() != LoanEmiOccurrenceStatus.PAID && o.getStatus() != LoanEmiOccurrenceStatus.SKIPPED).orElse(true);
                case "MUTUAL_FUND_SIP", "STOCK_MONTHLY_PLAN" -> investments.findByInvestmentIdAndTransactionKindAndScheduledMonth(id, InvestmentTransactionKind.SIP, month.atDay(1))
                        .map(t -> t.getStatus() != InvestmentTransactionStatus.CONFIRMED && t.getStatus() != InvestmentTransactionStatus.SKIPPED).orElse(true);
                default -> true;
            };
            if (!unpaid) continue;
            if (source.sourceType().equals("CREDIT_CARD_BILL") && cardBills != null) {
                bills = bills.add(source.plannedAmount().subtract(cardBills.paid(user, id, month)).max(BigDecimal.ZERO));
                continue;
            }
            if (bucket.key().equals("PLANNED_INVESTING")) investing = investing.add(source.plannedAmount());
            else if (bucket.key().equals("COMMITMENT_SAVINGS")) savings = savings.add(source.plannedAmount());
            else bills = bills.add(source.plannedAmount());
        }
        return new Overview(bills.add(investing), investing, savings);
    }
    public record Overview(BigDecimal stillToPay, BigDecimal plannedInvesting, BigDecimal plannedSavings) { }
}
