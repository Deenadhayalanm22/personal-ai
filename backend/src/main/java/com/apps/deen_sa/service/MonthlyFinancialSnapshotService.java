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
import com.apps.deen_sa.repository.UserRecurringCommitmentRepository;
import com.apps.deen_sa.repository.UserCreditCardRepository;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Builds canonical current- and next-month snapshots. New buckets are added to this payload, not new snapshot tables.
 */
@Service
public class MonthlyFinancialSnapshotService {
    private static final int CALCULATION_VERSION = 4;
    private final MonthlyFinancialSnapshotRepository snapshots;
    private final UserLoanRepository loans;
    private final UserInvestmentRepository investments;
    private final UserRecurringCommitmentRepository recurringCommitments;
    private final UserCreditCardRepository creditCards;
    private final FinancialTransactionRepository transactions;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Autowired
    public MonthlyFinancialSnapshotService(MonthlyFinancialSnapshotRepository snapshots, UserLoanRepository loans,
                                           UserInvestmentRepository investments, UserRecurringCommitmentRepository recurringCommitments,
                                           UserCreditCardRepository creditCards, FinancialTransactionRepository transactions, Clock clock) {
        this.snapshots = snapshots; this.loans = loans; this.investments = investments; this.recurringCommitments = recurringCommitments; this.creditCards = creditCards; this.transactions = transactions; this.clock = clock;
    }
    /** Compatibility constructor for focused unit tests predating FIN-020. */
    public MonthlyFinancialSnapshotService(MonthlyFinancialSnapshotRepository snapshots, UserLoanRepository loans,
                                           UserInvestmentRepository investments, Clock clock) {
        this(snapshots, loans, investments, null, null, null, clock);
    }

    /** FIN-018: a missing legacy snapshot is built once; subsequent story reads perform one snapshot lookup. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlySnapshot current(AppUserEntity user) {
        return projection(user, currentMonth(user));
    }

    /** FIN-018: the next-month runway uses the identical projection and persistence model. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlySnapshot next(AppUserEntity user) {
        return projection(user, currentMonth(user).plusMonths(1));
    }

    private MonthlySnapshot projection(AppUserEntity user, YearMonth month) {
        return snapshots.findByUserIdAndScopeMonth(user.getId(), month.atDay(1))
                .filter(snapshot -> snapshot.getCalculationVersion() == CALCULATION_VERSION)
                .map(this::parse).orElseGet(() -> rebuild(user, month));
    }

    /** FIN-018: source writers keep both runway months in lockstep with source data. */
    @Transactional
    public void refreshCurrent(AppUserEntity user) {
        YearMonth month = currentMonth(user);
        rebuild(user, month);
        rebuild(user, month.plusMonths(1));
    }

    private MonthlySnapshot rebuild(AppUserEntity user, YearMonth month) {
        List<Source> debt = loans.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().filter(loan -> hasEmiIn(loan, month))
                .map(loan -> loanSource(loan, month)).toList();
        List<Source> investing = investments.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().filter(investment -> hasSipIn(investment, month))
                .map(investment -> new Source("MUTUAL_FUND_SIP", String.valueOf(investment.getId()), investment.getDisplayNameSnapshot(), investment.getSipAmount(),
                        investment.getSipDay() == null ? null : month.atDay(Math.min(investment.getSipDay(), month.lengthOfMonth())),
                        "Mutual fund SIP", "Active SIP", null, null, null)).toList();
        List<Source> essential = (recurringCommitments == null ? List.<com.apps.deen_sa.entity.UserRecurringCommitmentEntity>of() : recurringCommitments.findAllOwned(user.getId())).stream().filter(commitment ->
                        commitment.getStatus() == com.apps.deen_sa.domain.RecurringCommitmentStatus.ACTIVE
                                && !month.atDay(1).isBefore(commitment.getEffectiveMonth()))
                .map(commitment -> new Source("RECURRING_COMMITMENT", String.valueOf(commitment.getId()), commitment.getLabel(), commitment.getPlanningAmount(),
                        commitment.getDueDay() == null ? null : month.atDay(Math.min(commitment.getDueDay(), month.lengthOfMonth())),
                        commitment.getCategory() == null ? "Essential living" : commitment.getCategory(),
                        commitment.getAmountMode() == com.apps.deen_sa.domain.CommitmentAmountMode.RECENT_BILL_ESTIMATE ? "Recent-bill estimate" : "Monthly planning amount", null, null, null)).toList();
        List<Source> cardBills = (creditCards == null || transactions == null ? List.<com.apps.deen_sa.entity.UserCreditCardEntity>of() : creditCards.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())).stream()
                .map(card -> creditCardSource(user, card, month)).filter(source -> source.plannedAmount().signum() > 0).toList();
        Bucket debtBucket = bucket("DEBT_REPAYMENTS", "Debt repayments", debt);
        Bucket investingBucket = bucket("PLANNED_INVESTING", "Planned investing", investing);
        Bucket essentialBucket = bucket("ESSENTIAL_LIVING", "Essential living", essential);
        Bucket cardBucket = bucket("CREDIT_CARD_BILLS", "Credit-card bills", cardBills);
        MonthlySnapshot value = new MonthlySnapshot(month.toString(), user.getCurrency(), CALCULATION_VERSION,
                debtBucket.plannedAmount().add(investingBucket.plannedAmount()).add(essentialBucket.plannedAmount()).add(cardBucket.plannedAmount()), List.of(debtBucket, investingBucket, essentialBucket, cardBucket));
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
    /** A due-month projection is the statement ending before that due date; transaction rows remain actual spending, never another expense. */
    private Source creditCardSource(AppUserEntity user, com.apps.deen_sa.entity.UserCreditCardEntity card, YearMonth dueMonth) {
        YearMonth statementMonth = card.getDueDay() > card.getStatementDay() ? dueMonth : dueMonth.minusMonths(1);
        LocalDate statementEnd = statementMonth.atDay(card.getStatementDay());
        LocalDate periodStart = statementMonth.minusMonths(1).atDay(card.getStatementDay()).plusDays(1);
        BigDecimal amount = transactions.sumVisibleByAccountAndPeriod(user.getId(), card.getAccountReference().getId(), periodStart, statementEnd.plusDays(1));
        return new Source("CREDIT_CARD_BILL", String.valueOf(card.getId()), card.getCardName(), amount,
                dueMonth.atDay(card.getDueDay()), "Credit-card bill", card.getIssuerName() + " · statement " + statementEnd + " · " + periodStart + " to " + statementEnd, null, null, null);
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
