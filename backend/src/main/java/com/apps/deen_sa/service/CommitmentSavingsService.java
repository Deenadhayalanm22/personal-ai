package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentRecurrenceUnit;
import com.apps.deen_sa.domain.RecurringCommitmentStatus;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** FIN-020 — user-confirmed savings toward one upcoming commitment payment. */
@Service
public class CommitmentSavingsService {
    private final CommitmentSavingsPlanRepository plans;
    private final CommitmentSavingsEntryRepository entries;
    private final UserRecurringCommitmentRepository commitments;
    private final MonthlyFinancialSnapshotService snapshots;
    private final Clock clock;
    public CommitmentSavingsService(CommitmentSavingsPlanRepository plans, CommitmentSavingsEntryRepository entries,
                                    UserRecurringCommitmentRepository commitments, MonthlyFinancialSnapshotService snapshots, Clock clock) {
        this.plans = plans; this.entries = entries; this.commitments = commitments; this.snapshots = snapshots; this.clock = clock;
    }
    private WebApiException invalid(String reason) { return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_SAVINGS_PLAN", reason); }
    private UserRecurringCommitmentEntity owned(AppUserEntity user, Long id) {
        return commitments.findOwned(id, user.getId()).orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "COMMITMENT_NOT_FOUND", "Commitment not found"));
    }
    private LocalDate today(AppUserEntity user) { return LocalDate.now(clock.withZone(java.time.ZoneId.of(user.getTimezone()))); }
    private CommitmentSavingsPlanEntity plan(AppUserEntity user, Long id) {
        owned(user, id);
        return plans.findByCommitmentIdOrderByTargetDateDesc(id).stream().findFirst().orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "SAVINGS_PLAN_NOT_FOUND", "Savings plan not found"));
    }
    @Transactional(readOnly = true)
    public PlanView preview(AppUserEntity user, Long id) {
        var c = owned(user, id);
        LocalDate date = c.getNextExpectedDate(), now = today(user);
        if (c.getStatus() != RecurringCommitmentStatus.ACTIVE || date == null || !date.isAfter(now)
                || (c.getRecurrenceUnit() == CommitmentRecurrenceUnit.MONTH && c.getRecurrenceInterval() == 1)
                || c.getRecurrenceUnit() == CommitmentRecurrenceUnit.DAY || c.getRecurrenceUnit() == CommitmentRecurrenceUnit.WEEK)
            throw invalid("Saving is available for an upcoming commitment less frequent than monthly");
        long months = ChronoUnit.MONTHS.between(YearMonth.from(now), YearMonth.from(date));
        if (months < 1) throw invalid("Choose a payment in a later month");
        if (plans.findByCommitmentIdAndTargetDate(id, date).isPresent()) throw invalid("A savings plan already exists for this payment");
        BigDecimal target = c.getPlanningAmount();
        BigDecimal monthly = target.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal finalAmount = target.subtract(monthly.multiply(BigDecimal.valueOf(months - 1)));
        if (finalAmount.signum() <= 0) throw invalid("Target is too small for monthly contributions");
        return new PlanView(null, id, date, target, YearMonth.from(now).toString(), (int) months, monthly, finalAmount,
                BigDecimal.ZERO, BigDecimal.ZERO, target, "UPCOMING", null, List.of());
    }
    @Transactional
    public PlanView create(AppUserEntity user, Long id, CreateRequest request) {
        PlanView suggestion = preview(user, id);
        BigDecimal chosen = request == null || request.monthlyAmount() == null ? suggestion.monthlyAmount() : request.monthlyAmount().setScale(2, RoundingMode.HALF_UP);
        if (chosen.signum() <= 0) throw invalid("Monthly amount must be positive");
        var p = new CommitmentSavingsPlanEntity();
        p.setCommitment(owned(user, id)); p.setTargetDate(suggestion.targetDate()); p.setTargetAmount(suggestion.targetAmount());
        p.setStartMonth(YearMonth.parse(suggestion.startMonth()).atDay(1)); p.setMonthlyAmount(chosen);
        p.setFinalAmount(chosen.compareTo(suggestion.monthlyAmount()) == 0 ? suggestion.finalAmount() : chosen);
        p.setCreatedAt(java.time.Instant.now(clock)); plans.saveAndFlush(p); snapshots.refreshCurrent(user);
        return view(p, YearMonth.from(today(user)));
    }
    @Transactional(readOnly = true)
    public PlanView get(AppUserEntity user, Long id) { return view(plan(user, id), YearMonth.from(today(user))); }
    @Transactional(readOnly = true)
    public List<PlanView> list(AppUserEntity user) { return allOwned(user).stream().map(p -> view(p, YearMonth.from(today(user)))).toList(); }
    @Transactional
    public PlanView record(AppUserEntity user, Long id, EntryRequest request) { return outcome(user, id, request, false); }
    @Transactional
    public PlanView skip(AppUserEntity user, Long id, EntryRequest request) { return outcome(user, id, request, true); }
    private PlanView outcome(AppUserEntity user, Long id, EntryRequest request, boolean skip) {
        var p = plan(user, id);
        YearMonth month = YearMonth.from(today(user));
        if (saved(p).compareTo(p.getTargetAmount()) >= 0) throw invalid("Savings target is already ready for payment");
        if (request != null && request.month() != null && !month.toString().equals(request.month())) throw invalid("Only this month's savings can be recorded");
        if (month.isBefore(YearMonth.from(p.getStartMonth())) || !month.isBefore(YearMonth.from(p.getTargetDate()))) throw invalid("No savings contribution is due this month");
        if (entries.findByPlanIdAndScheduledMonth(p.getId(), month.atDay(1)).isPresent()) throw invalid("This month already has an outcome");
        BigDecimal amount = skip ? null : request == null ? null : request.amount();
        if (!skip && (amount == null || amount.signum() <= 0)) throw invalid("Enter a positive amount actually set aside");
        var e = new CommitmentSavingsEntryEntity(); e.setPlan(p); e.setScheduledMonth(month.atDay(1)); e.setStatus(skip ? "SKIPPED" : "SAVED");
        e.setAmount(skip ? null : amount.setScale(2, RoundingMode.HALF_UP)); e.setRecordedAt(today(user)); entries.saveAndFlush(e);
        snapshots.refreshCurrent(user); return view(p, month);
    }
    @Transactional
    public void allocate(AppUserEntity user, Long id, LocalDate targetDate, BigDecimal used, BigDecimal actualAmount) {
        if (used == null) return;
        var p = plans.findByCommitmentIdAndTargetDate(id, targetDate).orElseThrow(() -> invalid("No savings plan for this payment"));
        owned(user, id);
        BigDecimal available = saved(p).subtract(p.getUsedAmount());
        if (used.signum() < 0 || used.compareTo(available) > 0 || used.compareTo(actualAmount) > 0) throw invalid("Savings used exceeds recorded savings or payment");
        p.setUsedAmount(p.getUsedAmount().add(used).setScale(2, RoundingMode.HALF_UP)); plans.saveAndFlush(p);
    }
    @Transactional(readOnly = true)
    public List<CommitmentSavingsPlanEntity> allOwned(AppUserEntity user) { return plans.findByCommitmentUserId(user.getId()); }
    @Transactional(readOnly = true)
    public boolean hasOutcome(Long planId, YearMonth month) { return entries.findByPlanIdAndScheduledMonth(planId, month.atDay(1)).isPresent(); }
    @Transactional(readOnly = true)
    public BigDecimal saved(CommitmentSavingsPlanEntity p) { return entries.findByPlanIdOrderByScheduledMonthAsc(p.getId()).stream().filter(e -> "SAVED".equals(e.getStatus())).map(CommitmentSavingsEntryEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private PlanView view(CommitmentSavingsPlanEntity p, YearMonth now) {
        List<EntryView> history = entries.findByPlanIdOrderByScheduledMonthAsc(p.getId()).stream()
                .map(e -> new EntryView(YearMonth.from(e.getScheduledMonth()).toString(), e.getStatus(), e.getAmount(), e.getRecordedAt())).toList();
        BigDecimal saved = saved(p), available = saved.subtract(p.getUsedAmount());
        int months = (int) ChronoUnit.MONTHS.between(YearMonth.from(p.getStartMonth()), YearMonth.from(p.getTargetDate()));
        String status = saved.compareTo(p.getTargetAmount()) >= 0 ? "READY" : now.isBefore(YearMonth.from(p.getStartMonth())) || !now.isBefore(YearMonth.from(p.getTargetDate())) ? "UPCOMING"
                : history.stream().anyMatch(e -> e.month().equals(now.toString())) ? "COMPLETED" : "DUE";
        BigDecimal future = BigDecimal.ZERO;
        for (YearMonth month = YearMonth.from(p.getStartMonth()); month.isBefore(YearMonth.from(p.getTargetDate())); month = month.plusMonths(1)) {
            YearMonth scheduled = month;
            if (!month.isBefore(now) && history.stream().noneMatch(e -> e.month().equals(scheduled.toString())))
                future = future.add(month.equals(YearMonth.from(p.getTargetDate()).minusMonths(1)) ? p.getFinalAmount() : p.getMonthlyAmount());
        }
        BigDecimal shortfall = p.getTargetAmount().subtract(saved.add(future));
        return new PlanView(p.getId(), p.getCommitment().getId(), p.getTargetDate(), p.getTargetAmount(), YearMonth.from(p.getStartMonth()).toString(), months, p.getMonthlyAmount(), p.getFinalAmount(), saved, p.getUsedAmount(), available, status, shortfall.max(BigDecimal.ZERO), history);
    }
    public record CreateRequest(BigDecimal monthlyAmount) { }
    public record EntryRequest(String month, BigDecimal amount) { }
    public record EntryView(String month, String status, BigDecimal amount, LocalDate recordedAt) { }
    public record PlanView(Long id, Long commitmentId, LocalDate targetDate, BigDecimal targetAmount, String startMonth, int months,
                           BigDecimal monthlyAmount, BigDecimal finalAmount, BigDecimal saved, BigDecimal used, BigDecimal available,
                           String currentStatus, BigDecimal projectedShortfall, List<EntryView> history) { }
}
