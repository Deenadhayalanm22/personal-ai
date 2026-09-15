package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.InvestmentAssetType;
import com.apps.deen_sa.domain.InvestmentSipStatus;
import com.apps.deen_sa.domain.LoanStatus;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.MonthlyFinancialSnapshotEntity;
import com.apps.deen_sa.entity.UserInvestmentEntity;
import com.apps.deen_sa.entity.UserLoanEntity;
import com.apps.deen_sa.repository.MonthlyFinancialSnapshotRepository;
import com.apps.deen_sa.repository.UserInvestmentRepository;
import com.apps.deen_sa.repository.UserLoanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * FIN-018 — Pin the live monthly commitment story. See docs/jira/personal-expense/FIN-EPIC-005-planning.md.
 * Builds the canonical current-month snapshot. New buckets are added to this payload, not new snapshot tables.
 */
@Service
public class MonthlyFinancialSnapshotService {
    private static final int CALCULATION_VERSION = 2;
    private final MonthlyFinancialSnapshotRepository snapshots;
    private final UserLoanRepository loans;
    private final UserInvestmentRepository investments;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public MonthlyFinancialSnapshotService(MonthlyFinancialSnapshotRepository snapshots, UserLoanRepository loans,
                                           UserInvestmentRepository investments, Clock clock) {
        this.snapshots = snapshots; this.loans = loans; this.investments = investments; this.clock = clock;
    }

    /** FIN-018: a missing legacy snapshot is built once; subsequent story reads perform one snapshot lookup. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlySnapshot current(AppUserEntity user) {
        YearMonth month = currentMonth(user);
        return snapshots.findByUserIdAndScopeMonth(user.getId(), month.atDay(1))
                .filter(snapshot -> snapshot.getCalculationVersion() == CALCULATION_VERSION)
                .map(this::parse).orElseGet(() -> rebuild(user, month));
    }

    /** FIN-018: source writers call this inside their transaction, keeping the current snapshot in lockstep with source data. */
    @Transactional
    public void refreshCurrent(AppUserEntity user) { rebuild(user, currentMonth(user)); }

    private MonthlySnapshot rebuild(AppUserEntity user, YearMonth month) {
        List<Source> debt = loans.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().filter(loan -> hasEmiIn(loan, month))
                .map(loan -> loanSource(loan, month)).toList();
        List<Source> investing = investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().filter(investment -> hasSipIn(investment, month))
                .map(investment -> new Source("MUTUAL_FUND_SIP", String.valueOf(investment.getId()), investment.getDisplayNameSnapshot(), investment.getSipAmount(),
                        investment.getSipDay() == null ? null : month.atDay(Math.min(investment.getSipDay(), month.lengthOfMonth())),
                        "Mutual fund SIP", "Active SIP", null, null, null)).toList();
        Bucket debtBucket = bucket("DEBT_REPAYMENTS", "Debt repayments", debt);
        Bucket investingBucket = bucket("PLANNED_INVESTING", "Planned investing", investing);
        MonthlySnapshot value = new MonthlySnapshot(month.toString(), user.getCurrency(), CALCULATION_VERSION,
                debtBucket.plannedAmount().add(investingBucket.plannedAmount()), List.of(debtBucket, investingBucket));
        String payload = write(value);
        String fingerprint = fingerprint(payload);
        MonthlyFinancialSnapshotEntity entity = snapshots.findByUserIdAndScopeMonth(user.getId(), month.atDay(1)).orElseGet(() -> {
            MonthlyFinancialSnapshotEntity created = new MonthlyFinancialSnapshotEntity();
            created.setId(UUID.randomUUID()); created.setUser(user); created.setScopeMonth(month.atDay(1)); return created;
        });
        entity.setCalculationVersion(CALCULATION_VERSION); entity.setPayload(payload); entity.setSourceFingerprint(fingerprint); entity.setUpdatedAt(Instant.now(clock));
        snapshots.save(entity);
        return value;
    }

    private Bucket bucket(String key, String label, List<Source> sources) {
        return new Bucket(key, label, sources.stream().map(Source::plannedAmount).reduce(BigDecimal.ZERO, BigDecimal::add), sources);
    }
    private Source loanSource(UserLoanEntity loan, YearMonth month) {
        YearMonth end = YearMonth.from(loan.getFirstEmiDueDate()).plusMonths(loan.getTotalTenureMonths() - 1L);
        int remaining = (int) java.time.temporal.ChronoUnit.MONTHS.between(month, end) + 1;
        return new Source("LOAN", String.valueOf(loan.getId()), loan.getLoanName(), loan.getMonthlyEmiAmount(),
                LocalDate.of(month.getYear(), month.getMonth(), Math.min(loan.getFirstEmiDueDate().getDayOfMonth(), month.lengthOfMonth())),
                "Loan EMI", loan.getLenderName(), remaining, end.toString(), end.plusMonths(1).toString());
    }
    private boolean hasEmiIn(UserLoanEntity loan, YearMonth month) {
        if (loan.getStatus() != LoanStatus.ACTIVE) return false;
        YearMonth first = YearMonth.from(loan.getFirstEmiDueDate());
        return !month.isBefore(first) && !month.isAfter(first.plusMonths(loan.getTotalTenureMonths() - 1L));
    }
    private boolean hasSipIn(UserInvestmentEntity investment, YearMonth month) {
        return investment.getAssetType() == InvestmentAssetType.MUTUAL_FUND && investment.getSipStatus() == InvestmentSipStatus.ACTIVE
                && investment.getSipAmount() != null && investment.getSipStartMonth() != null && !month.isBefore(YearMonth.from(investment.getSipStartMonth()));
    }
    private YearMonth currentMonth(AppUserEntity user) { return YearMonth.now(clock.withZone(java.time.ZoneId.of(user.getTimezone()))); }
    private String write(MonthlySnapshot value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("Could not save monthly financial snapshot", e); } }
    private MonthlySnapshot parse(MonthlyFinancialSnapshotEntity value) { try { return mapper.readValue(value.getPayload(), MonthlySnapshot.class); } catch (Exception e) { throw new IllegalStateException("Stored monthly financial snapshot is invalid", e); } }
    private String fingerprint(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record MonthlySnapshot(String month, String currency, int calculationVersion, BigDecimal fullIntendedCommitment, List<Bucket> commitmentBuckets) { }
    public record Bucket(String key, String label, BigDecimal plannedAmount, List<Source> sources) { }
    public record Source(String sourceType, String sourceId, String label, BigDecimal plannedAmount, LocalDate dueDate, String category,
                         String detail, Integer remainingPayments, String endsInMonth, String freesFromMonth) { }
}
