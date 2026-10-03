package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.UserIncomeProfileRepository;
import com.apps.deen_sa.service.MonthlyFinancialSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** FIN-EPIC-005/006: canonical planning facts and non-persistent what-if arithmetic. */
@Service
public class MonthlyPlanningTool {
    private final MonthlyFinancialSnapshotService snapshots;
    private final UserIncomeProfileRepository income;
    private final Clock clock;
    public MonthlyPlanningTool(MonthlyFinancialSnapshotService snapshots, UserIncomeProfileRepository income, Clock clock) {
        this.snapshots = snapshots; this.income = income; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public Plan read(AppUserEntity user, MonthRequest request) {
        return calculate(user, month(user, request == null ? null : request.month()), List.of(), false);
    }
    @Transactional(readOnly = true)
    public Plan simulate(AppUserEntity user, ScenarioRequest request) {
        if (request == null || request.adjustments() == null || request.adjustments().isEmpty() || request.adjustments().size() > 20)
            throw ExpenseQueryTool.invalid("Supply 1–20 hypothetical reductions from the monthly plan.");
        YearMonth month = month(user, request.month());
        if (!month.equals(current(user).plusMonths(1)))
            throw ExpenseQueryTool.invalid("Scenarios support next month only; recorded current-month payments cannot be reduced hypothetically.");
        return calculate(user, month, request.adjustments(), true);
    }
    private Plan calculate(AppUserEntity user, YearMonth month, List<Adjustment> adjustments, boolean scenario) {
        var snapshot = snapshots.preview(user, month);
        var changes = new HashMap<String,BigDecimal>();
        for (var adjustment : adjustments) {
            if (adjustment == null || adjustment.sourceKey() == null || adjustment.newAmount() == null
                    || adjustment.newAmount().signum() < 0 || adjustment.newAmount().scale() > 2 || adjustment.newAmount().precision() > 19
                    || changes.putIfAbsent(adjustment.sourceKey(), adjustment.newAmount()) != null)
                throw ExpenseQueryTool.invalid("Each source must appear once with a nonnegative amount of at most two decimals.");
        }
        List<Item> items = new ArrayList<>();
        List<Bucket> buckets = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var bucket : snapshot.commitmentBuckets()) {
            BigDecimal bucketTotal = BigDecimal.ZERO;
            for (var source : bucket.sources()) {
                String key = source.sourceType() + ":" + source.sourceId() + ":" + Objects.toString(source.dueDate(), month.toString());
                // A commitment may have several savings targets in the same month.
                if ("COMMITMENT_SAVINGS".equals(source.sourceType())) key += ":" + Objects.toString(source.detail(), "");
                BigDecimal changed = changes.remove(key);
                if (changed != null && (changed.compareTo(source.plannedAmount()) > 0
                        || Set.of("LOAN", "CREDIT_CARD_BILL").contains(source.sourceType())))
                    throw ExpenseQueryTool.invalid("Use reductions to planned investing, savings or commitments. Loan/card obligations stay fixed without a recorded lender-approved change.");
                BigDecimal proposed = changed == null ? source.plannedAmount() : changed;
                String condition = switch (source.sourceType()) {
                    case "LOAN", "CREDIT_CARD_BILL" -> "Obligation: preserve payment; app skip is not lender permission.";
                    case "MUTUAL_FUND_SIP", "STOCK_MONTHLY_PLAN" -> "Planned investment: review provider terms and effect on investment goals before reducing.";
                    case "COMMITMENT_SAVINGS" -> "Earmarked saving: a reduction leaves less set aside for the target bill; the bill remains due.";
                    default -> "Payment flexibility is unverified. Confirm whether this item can be reduced or deferred.";
                };
                items.add(new Item(key, source.sourceType(), source.sourceId(), source.label(), bucket.key(), source.dueDate(),
                        source.plannedAmount(), proposed, source.plannedAmount().subtract(proposed), condition));
                bucketTotal = bucketTotal.add(proposed);
            }
            buckets.add(new Bucket(bucket.label(), bucket.plannedAmount(), bucketTotal));
            total = total.add(bucketTotal);
        }
        if (!changes.isEmpty()) throw ExpenseQueryTool.invalid("A source is no longer in this profile's monthly plan. Read the plan again.");
        if (items.size() > 500) throw ExpenseQueryTool.invalid("This plan has too many occurrences for chat; review the monthly commitment screen.");
        var profile = income.findById(user.getId()).orElse(null);
        BigDecimal salary = null;
        String incomeStatus = "NOT_SHARED";
        if (profile != null && "RANGE".equals(profile.getSalaryVisibility())) incomeStatus = "RANGE_ONLY";
        if (profile != null && "EXACT".equals(profile.getSalaryVisibility())) {
            if ("IRREGULAR".equals(profile.getSalaryFrequency())) incomeStatus = "IRREGULAR_INCOME";
            else if (profile.getExactMonthlySalary() != null && profile.getExactMonthlySalary().signum() > 0) {
                // The stored field is explicitly a monthly estimate, irrespective of pay frequency.
                salary = profile.getExactMonthlySalary(); incomeStatus = "EXACT_MONTHLY_ESTIMATE";
            }
        }
        BigDecimal baselineDifference = salary == null ? null : salary.subtract(snapshot.fullIntendedCommitment());
        BigDecimal proposedDifference = salary == null ? null : salary.subtract(total);
        return new Plan(scenario ? "scenario" : "plan", month.toString(), user.getCurrency(), snapshot.fullIntendedCommitment(), total,
                snapshot.fullIntendedCommitment().subtract(total), incomeStatus, baselineDifference, proposedDifference,
                List.copyOf(buckets), List.copyOf(items), List.of(
                "Monthly intended plan, not unpaid balance or a bank cash-flow forecast. Paid and planned items follow the existing Monthly Commitment calculation.",
                "Additional living costs and unrecorded obligations are not included. Do not add historic expenses, card charges, holdings or savings balances to this total.",
                "Income comparison uses a private monthly estimate, not confirmed money received. No exact comparison is available for a range, missing or irregular income.",
                scenario ? "Hypothetical only: no records changed. Reduced savings or investing affect future targets; deferred commitments remain obligations." : "Review source details before treating a planned allocation as adjustable."));
    }
    private YearMonth current(AppUserEntity user) { return YearMonth.now(clock.withZone(ZoneId.of(user.getTimezone()))); }
    private YearMonth month(AppUserEntity user, String value) {
        try {
            YearMonth month = YearMonth.parse(value);
            if (!month.equals(current(user)) && !month.equals(current(user).plusMonths(1))) throw new DateTimeException("Unsupported month");
            return month;
        } catch (DateTimeException | NullPointerException ex) { throw ExpenseQueryTool.invalid("Choose the current or next month using YYYY-MM."); }
    }
    public record MonthRequest(String month) {}
    public record Adjustment(String sourceKey, BigDecimal newAmount) {}
    public record ScenarioRequest(String month, List<Adjustment> adjustments) {}
    public record Bucket(String label, BigDecimal baseline, BigDecimal proposed) {}
    public record Item(String sourceKey, String sourceType, String sourceId, String label, String bucket, LocalDate dueDate,
                       BigDecimal baseline, BigDecimal proposed, BigDecimal reduction, String condition) {}
    public record Plan(String kind, String month, String currency, BigDecimal baselineTotal, BigDecimal proposedTotal,
                       BigDecimal reduction, String incomeStatus, BigDecimal baselineAfterIncome, BigDecimal proposedAfterIncome,
                       List<Bucket> buckets, List<Item> items, List<String> limitations) {}
}
