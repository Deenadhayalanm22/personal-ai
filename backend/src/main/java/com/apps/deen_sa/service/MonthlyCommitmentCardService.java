package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentContextType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import com.apps.deen_sa.repository.InvestmentTransactionRepository;
import com.apps.deen_sa.repository.LoanEmiOccurrenceRepository;
import com.apps.deen_sa.repository.RecurringCommitmentOccurrenceRepository;
import com.apps.deen_sa.domain.LoanEmiOccurrenceStatus;
import com.apps.deen_sa.domain.RecurringCommitmentOccurrenceStatus;
import com.apps.deen_sa.repository.UserActionItemRepository;
import com.apps.deen_sa.domain.InvestmentTransactionKind;
import com.apps.deen_sa.domain.InvestmentTransactionStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FIN-018 — Pin the live monthly commitment story. See docs/jira/personal-expense/FIN-EPIC-005-planning.md.
 * Produces the first planning story from the canonical monthly financial snapshot; it never recalculates loans/SIPs on a normal read.
 */
@Service
public class MonthlyCommitmentCardService {
    private final MonthlyFinancialSnapshotService snapshots;
    private final CommitmentCopyGenerator copyGenerator;
    private final Clock clock;
    private final CommitmentEnrichmentPipeline enrichment;
    private final FinancialTransactionRepository transactions;
    private final InvestmentTransactionRepository investmentTransactions;
    private final LoanEmiOccurrenceRepository loanOccurrences;
    @Autowired(required = false) private RecurringCommitmentOccurrenceRepository commitmentOccurrences;
    @Autowired(required = false) private CreditCardBillService cardBills;

    /** Retained for focused tests that exercise the commitment core without optional contributors. */
    public MonthlyCommitmentCardService(MonthlyFinancialSnapshotService snapshots, CommitmentCopyGenerator copyGenerator, Clock clock) {
        this(snapshots, copyGenerator, clock, new CommitmentEnrichmentPipeline(List.of(), List.of()), null, null, null, null);
    }
    public MonthlyCommitmentCardService(MonthlyFinancialSnapshotService snapshots, CommitmentCopyGenerator copyGenerator, Clock clock,
                                         CommitmentEnrichmentPipeline enrichment) { this(snapshots, copyGenerator, clock, enrichment, null, null, null, null); }
    /** Compatibility constructor for focused tests with optional transaction repositories. */
    public MonthlyCommitmentCardService(MonthlyFinancialSnapshotService snapshots, CommitmentCopyGenerator copyGenerator, Clock clock,
                                         CommitmentEnrichmentPipeline enrichment, FinancialTransactionRepository transactions,
                                         InvestmentTransactionRepository investmentTransactions) {
        this(snapshots, copyGenerator, clock, enrichment, transactions, investmentTransactions, null, null);
    }

    @Autowired
    public MonthlyCommitmentCardService(MonthlyFinancialSnapshotService snapshots, CommitmentCopyGenerator copyGenerator, Clock clock,
                                         CommitmentEnrichmentPipeline enrichment, FinancialTransactionRepository transactions,
                                         InvestmentTransactionRepository investmentTransactions, UserActionItemRepository userActions,
                                         LoanEmiOccurrenceRepository loanOccurrences) {
        this.snapshots = snapshots; this.copyGenerator = copyGenerator; this.clock = clock; this.enrichment = enrichment;
        this.transactions = transactions; this.investmentTransactions = investmentTransactions;
        this.loanOccurrences = loanOccurrences;
    }

    @Transactional(readOnly = true)
    public MonthlyCommitmentPresentationService.CommitmentPresentation currentFor(AppUserEntity user) {
        var snapshot = snapshots.current(user);
        var nextSnapshot = snapshots.next(user);
        YearMonth month = YearMonth.parse(snapshot.month());
        var debt = snapshot.commitmentBuckets().stream().filter(bucket -> "DEBT_REPAYMENTS".equals(bucket.key())).findFirst().orElseThrow();
        var investing = snapshot.commitmentBuckets().stream().filter(bucket -> "PLANNED_INVESTING".equals(bucket.key())).findFirst().orElseThrow();
        BigDecimal emiTotal = debt.plannedAmount(), sipTotal = investing.plannedAmount(), cardBillTotal = snapshot.commitmentBuckets().stream().filter(bucket -> "CREDIT_CARD_BILLS".equals(bucket.key())).findFirst().map(MonthlyFinancialSnapshotService.Bucket::plannedAmount).orElse(BigDecimal.ZERO), total = snapshot.fullIntendedCommitment();
        BigDecimal savingsTotal = snapshot.commitmentBuckets().stream().filter(bucket -> "COMMITMENT_SAVINGS".equals(bucket.key())).findFirst().map(MonthlyFinancialSnapshotService.Bucket::plannedAmount).orElse(BigDecimal.ZERO);
        var recurring = snapshot.commitmentBuckets().stream().filter(bucket -> "ESSENTIAL_LIVING".equals(bucket.key())).findFirst().orElse(null);
        String currency = user.getCurrency();
        String value = CommitmentMoneyFormatter.money(total, currency);
        List<MonthlyCommitmentPresentationService.Component> components = new ArrayList<>();
        if (emiTotal.signum() > 0) components.add(component("Debt repayments", emiTotal, currency));
        if (sipTotal.signum() > 0) components.add(component("Planned investing", sipTotal, currency));
        if (cardBillTotal.signum() > 0) components.add(component("Credit-card bills", cardBillTotal, currency));
        if (recurring != null && recurring.plannedAmount().signum() > 0) components.add(component(recurring.label(), recurring.plannedAmount(), currency));
        if (savingsTotal.signum() > 0) components.add(component("Saving for upcoming bills", savingsTotal, currency));
        if (cardBills != null && cardBillTotal.signum() > 0) {
            BigDecimal cardPaid = snapshot.commitmentBuckets().stream().filter(b -> b.key().equals("CREDIT_CARD_BILLS"))
                    .flatMap(b -> b.sources().stream()).map(source -> cardBills.paid(user, Long.valueOf(source.sourceId()), month).min(source.plannedAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            components.add(component("Card bills settled", cardPaid, currency));
        }
        BigDecimal completedSipTotal = completedSipTotal(investing, month);
        if (completedSipTotal.signum() > 0) components.add(component("SIP allocations complete", completedSipTotal, currency));
        BigDecimal completedRecurringTotal = recurring == null ? BigDecimal.ZERO : completedRecurringTotal(recurring, month);
        if (completedRecurringTotal.signum() > 0) components.add(component("Recurring commitments complete", completedRecurringTotal, currency));
        BigDecimal completedLoanTotal = completedLoanTotal(debt, month);
        if (emiTotal.signum() > 0) {
            BigDecimal remainingLoanTotal = emiTotal.subtract(completedLoanTotal);
            String progress = completedLoanTotal.multiply(BigDecimal.valueOf(100)).divide(emiTotal, 0, java.math.RoundingMode.HALF_UP) + "%";
            components.add(new MonthlyCommitmentPresentationService.Component("MONEY", "Loan payment progress", null, currency,
                    CommitmentMoneyFormatter.money(completedLoanTotal, currency) + " paid of " + CommitmentMoneyFormatter.money(emiTotal, currency)
                            + " · " + CommitmentMoneyFormatter.money(remainingLoanTotal, currency) + " left · " + progress));
            components.add(new MonthlyCommitmentPresentationService.Component("TEXT", "This month", null, null,
                    completedLoanTotal.compareTo(emiTotal) >= 0 ? "Paid · nothing due" : "Due · payment needed"));
        }

        String title = total.signum() > 0 ? "Your known monthly commitment" : "Your monthly commitment starts here";
        String body = total.signum() > 0
                ? commitmentBody(value, emiTotal, sipTotal, cardBillTotal, savingsTotal, currency)
                : "Add a loan or a mutual fund SIP and it will appear here as part of your monthly commitment.";
        String payoffFacts = debt.sources().stream().filter(source -> source.remainingPayments() != null)
                .map(source -> source.label() + ": " + source.remainingPayments() + " payments remaining, ends " + source.endsInMonth()
                        + ", frees " + CommitmentMoneyFormatter.money(source.plannedAmount(), currency) + "/month from " + source.freesFromMonth())
                .collect(Collectors.joining("; "));
        var soonest = debt.sources().stream().filter(source -> source.remainingPayments() != null)
                .min(java.util.Comparator.comparing(MonthlyFinancialSnapshotService.Source::remainingPayments)).orElse(null);
        String fallbackTitle = soonest == null ? "Your money squad" : soonest.label() + " exits next";
        String fallbackBody = soonest == null ? body : CommitmentMoneyFormatter.money(soonest.plannedAmount(), currency)
                + "/month is free from " + monthLabel(YearMonth.parse(soonest.freesFromMonth())) + ".";
        var fallback = new CommitmentCopyGenerator.Copy("🎬 Payment squad", "warm", "WARM_NOTICE",
                monthLabel(month) + " · One update", fallbackTitle, fallbackBody, "", "");
        var nextFallback = nextFallback(nextSnapshot, currency);
        Map<String, String> copyFacts = new HashMap<>(Map.of("month", monthLabel(month), "total", value,
                "debtRepayments", CommitmentMoneyFormatter.money(emiTotal, currency), "plannedInvesting", CommitmentMoneyFormatter.money(sipTotal, currency),
                "loanPayoffFacts", payoffFacts, "soonestPayoff", soonest == null ? "None" : soonest.label() + " frees "
                        + CommitmentMoneyFormatter.money(soonest.plannedAmount(), currency) + "/month from " + soonest.freesFromMonth()));
        copyFacts.put("nextMonth", monthLabel(YearMonth.parse(nextSnapshot.month())));
        copyFacts.put("nextTotal", CommitmentMoneyFormatter.money(nextSnapshot.fullIntendedCommitment(), currency));
        copyFacts.put("nextDebtRepayments", bucketAmount(nextSnapshot, "DEBT_REPAYMENTS", currency));
        copyFacts.put("nextPlannedInvesting", bucketAmount(nextSnapshot, "PLANNED_INVESTING", currency));
        copyFacts.put("nextEssentialLiving", bucketAmount(nextSnapshot, "ESSENTIAL_LIVING", currency));
        var runwayCopy = copyGenerator.generateCommitmentRunway(copyFacts,
                new CommitmentCopyGenerator.CommitmentRunwayCopy(fallback, nextFallback));
        var copy = runwayCopy.current();
        List<MonthlyCommitmentPresentationService.EvidenceTransaction> evidenceRows = evidenceRows(snapshot, currency);
        List<MonthlyCommitmentPresentationService.Action> actions = new ArrayList<>();
        if (!evidenceRows.isEmpty()) actions.add(new MonthlyCommitmentPresentationService.Action("OPEN_EVIDENCE", "View included commitments"));
        int candidates = transactions == null ? 0 : transactions.findCommitmentCandidates(user.getId(), month.atDay(1), month.plusMonths(1).atDay(1)).size();
        if (candidates > 0) actions.add(new MonthlyCommitmentPresentationService.Action("OPEN_COMMITMENT_REVIEW", candidates == 1 ? "Review 1 payment" : "Review " + candidates + " payments"));
        CommitmentContext context = enrichment.enrich(new CommitmentEnrichmentRequest(user, CommitmentContextType.MONTHLY_COMMITMENT, month,
                Map.of("commitment.total", total)));
        CommitmentInsight salaryInsight = context.insights().stream()
                .filter(insight -> insight.key().startsWith("COMMITMENT_INCOME_"))
                .findFirst().orElse(null);
        addSalaryContext(components, salaryInsight, total, currency);
        var card = new MonthlyCommitmentPresentationService.CardDto("commitment", 1, "COMMITMENT", copy.theme(),
                copy.eyebrow(), copy.title(), copy.body(), List.copyOf(components), List.copyOf(actions));
        var nextCard = nextMonthCard(nextSnapshot, currency, runwayCopy.next(), salaryInsight);
        List<MonthlyCommitmentPresentationService.CardDto> cards = new ArrayList<>(List.of(card, nextCard));
        if (context.insights().isEmpty() && total.signum() > 0) {
            cards.add(new MonthlyCommitmentPresentationService.CardDto("commitment-context", cards.size() + 1, "CONTEXT", "CALM_CONTEXT",
                    "OPTIONAL · PRIVATE", "Want a clearer monthly view?", "Add an income range to see a private affordability lens for your commitments. "
                    + "It never changes your balance or records an income transaction.", List.of(),
                    List.of(new MonthlyCommitmentPresentationService.Action("OPEN_SALARY_OUTLOOK", "Add income context"))));
        }
        var period = new MonthlyCommitmentPresentationService.PeriodDto("MONTH", month.atDay(1), month.atEndOfMonth(), monthLabel(month));
        var face = new MonthlyCommitmentPresentationService.CardFace(copy.heading(), value, copy.faceTheme());
        var evidence = new MonthlyCommitmentPresentationService.EvidenceDto("Commitment sources for the selected month", evidenceRows.size(),
                component("Monthly total", total, currency), List.of(), List.of(), Map.of(
                "commitment", evidence(snapshot, currency), "next-commitment", evidence(nextSnapshot, currency)));
        return new MonthlyCommitmentPresentationService.CommitmentPresentation("monthly-commitment", CommitmentContextType.MONTHLY_COMMITMENT.name(), 2,
                Instant.now(clock), period, face, List.copyOf(cards), evidence);
    }


    private MonthlyCommitmentPresentationService.CardDto nextMonthCard(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency,
                                                        CommitmentCopyGenerator.Copy copy, CommitmentInsight salaryInsight) {
        List<MonthlyCommitmentPresentationService.EvidenceTransaction> rows = evidenceRows(snapshot, currency);
        List<MonthlyCommitmentPresentationService.Action> actions = rows.isEmpty() ? List.of()
                : List.of(new MonthlyCommitmentPresentationService.Action("OPEN_EVIDENCE", "View " + monthLabel(YearMonth.parse(snapshot.month())) + " commitments"));
        List<MonthlyCommitmentPresentationService.Component> components = new ArrayList<>(components(snapshot, currency));
        addSalaryContext(components, salaryInsight, snapshot.fullIntendedCommitment(), currency);
        return new MonthlyCommitmentPresentationService.CardDto("next-commitment", 2, "COMMITMENT", copy.theme(),
                copy.eyebrow(), copy.title(), copy.body(), List.copyOf(components), actions);
    }

    private void addSalaryContext(List<MonthlyCommitmentPresentationService.Component> components, CommitmentInsight insight, BigDecimal commitment, String currency) {
        if (insight == null) return;
        if ("COMMITMENT_INCOME_EXACT".equals(insight.key())) {
            BigDecimal salary = (BigDecimal) insight.facts().get("monthlySalary");
            BigDecimal percent = commitment.multiply(BigDecimal.valueOf(100)).divide(salary, 1, java.math.RoundingMode.HALF_UP);
            components.add(new MonthlyCommitmentPresentationService.Component("PERCENTAGE", "Of monthly income", percent, null,
                    percent.stripTrailingZeros().toPlainString() + "%"));
            BigDecimal difference = salary.subtract(commitment);
            components.add(new MonthlyCommitmentPresentationService.Component("MONEY", "Income after planned commitments", difference, currency,
                    CommitmentMoneyFormatter.money(difference.abs(), currency)));
            return;
        }
        components.add(new MonthlyCommitmentPresentationService.Component("PRIVATE", "Income context", null, null, "Range shared privately"));
    }

    private CommitmentCopyGenerator.Copy nextFallback(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        YearMonth month = YearMonth.parse(snapshot.month());
        String total = CommitmentMoneyFormatter.money(snapshot.fullIntendedCommitment(), currency);
        String title = snapshot.fullIntendedCommitment().signum() > 0 ? "Next month is queued" : "A clear runway ahead";
        String body = snapshot.fullIntendedCommitment().signum() > 0
                ? total + " is already planned for " + monthLabel(month) + ". Keep it ready before the month begins."
                : "No known loan EMI or SIP is planned for " + monthLabel(month) + " yet.";
        return new CommitmentCopyGenerator.Copy("🎬 Payment squad", "warm", "WARM_NOTICE",
                "NEXT MONTH · READY", title, body, "", "");
    }

    private String bucketAmount(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String key, String currency) {
        return snapshot.commitmentBuckets().stream().filter(bucket -> key.equals(bucket.key())).findFirst()
                .map(bucket -> CommitmentMoneyFormatter.money(bucket.plannedAmount(), currency)).orElse(CommitmentMoneyFormatter.money(BigDecimal.ZERO, currency));
    }

    /** Portfolio progress is derived from confirmed SIP allocations; planned totals remain snapshot-owned. */
    private BigDecimal completedSipTotal(MonthlyFinancialSnapshotService.Bucket investing, YearMonth month) {
        if (investmentTransactions == null) return BigDecimal.ZERO;
        List<Long> ids = investing.sources().stream().filter(source -> "MUTUAL_FUND_SIP".equals(source.sourceType()) || "STOCK_MONTHLY_PLAN".equals(source.sourceType()))
                .map(source -> Long.valueOf(source.sourceId())).toList();
        if (ids.isEmpty()) return BigDecimal.ZERO;
        return investmentTransactions.findByInvestmentIdInAndTransactionKindAndScheduledMonthAndStatus(ids,
                        InvestmentTransactionKind.SIP, month.atDay(1), InvestmentTransactionStatus.CONFIRMED).stream()
                .map(tx -> tx.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal completedLoanTotal(MonthlyFinancialSnapshotService.Bucket debt, YearMonth month) {
        if (loanOccurrences == null) return BigDecimal.ZERO;
        return debt.sources().stream().filter(source -> "LOAN".equals(source.sourceType()))
                .map(source -> loanOccurrences.findByLoanIdAndDueMonth(Long.valueOf(source.sourceId()), month.atDay(1))
                        .filter(value -> value.getStatus() == LoanEmiOccurrenceStatus.PAID).map(value -> value.getPaidAmount()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal completedRecurringTotal(MonthlyFinancialSnapshotService.Bucket recurring, YearMonth month) {
        if (commitmentOccurrences == null) return BigDecimal.ZERO;
        return recurring.sources().stream().filter(source -> "RECURRING_COMMITMENT".equals(source.sourceType()))
                .map(source -> {
                    Long id = Long.valueOf(source.sourceId());
                    var exact = source.dueDate() == null ? java.util.Optional.<com.apps.deen_sa.entity.RecurringCommitmentOccurrenceEntity>empty()
                            : commitmentOccurrences.findByCommitmentIdAndScheduledMonth(id, source.dueDate());
                    return exact.or(() -> commitmentOccurrences.findByCommitmentIdAndScheduledMonth(id, month.atDay(1)))
                            .filter(outcome -> outcome.getStatus() == RecurringCommitmentOccurrenceStatus.COMPLETED)
                            .map(outcome -> source.plannedAmount()).orElse(BigDecimal.ZERO);
                }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<MonthlyCommitmentPresentationService.Component> components(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        List<MonthlyCommitmentPresentationService.Component> components = new ArrayList<>();
        snapshot.commitmentBuckets().forEach(bucket -> {
            if (bucket.plannedAmount().signum() > 0) components.add(component(bucket.label(), bucket.plannedAmount(), currency));
        });
        return List.copyOf(components);
    }

    private MonthlyCommitmentPresentationService.EvidenceDto evidence(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        List<MonthlyCommitmentPresentationService.EvidenceTransaction> rows = evidenceRows(snapshot, currency);
        return new MonthlyCommitmentPresentationService.EvidenceDto("Commitment sources by bucket", rows.size(),
                component("Monthly total", snapshot.fullIntendedCommitment(), currency), rows);
    }

    private List<MonthlyCommitmentPresentationService.EvidenceTransaction> evidenceRows(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        return snapshot.commitmentBuckets().stream().flatMap(bucket -> bucket.sources().stream())
                .map(source -> evidence(source, currency)).toList();
    }

    private MonthlyCommitmentPresentationService.Component component(String label, BigDecimal amount, String currency) {
        return new MonthlyCommitmentPresentationService.Component("MONEY", label, amount, currency, CommitmentMoneyFormatter.money(amount, currency));
    }

    private String commitmentBody(String total, BigDecimal debt, BigDecimal investing, BigDecimal cardBills, BigDecimal savings, String currency) {
        if (savings.signum() > 0) return "Your planned commitments total " + total + " this month, including "
                + CommitmentMoneyFormatter.money(savings, currency) + " you chose to set aside for an upcoming payment.";
        if (cardBills.signum() > 0) return "Your full intended commitment is " + total + ", including "
                + CommitmentMoneyFormatter.money(cardBills, currency) + " in credit-card bills due this month.";
        if (debt.signum() > 0 && investing.signum() > 0) {
            return "Your full intended commitment is " + total + ": "
                    + CommitmentMoneyFormatter.money(debt, currency) + " in debt repayments and "
                    + CommitmentMoneyFormatter.money(investing, currency) + " in planned investing.";
        }
        if (debt.signum() > 0) return "Your required debt repayments total " + total + " this month.";
        if (investing.signum() > 0) return "Your planned investing total is " + total + " this month.";
        if (cardBills.signum() > 0) return "Your credit-card bill due is " + CommitmentMoneyFormatter.money(cardBills, currency) + " this month.";
        return "Your known planned payments total " + total + " this month.";
    }

    private MonthlyCommitmentPresentationService.EvidenceTransaction evidence(MonthlyFinancialSnapshotService.Source source, String currency) {
        String date = source.dueDate() == null ? "Every month" : source.dueDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM"));
        return new MonthlyCommitmentPresentationService.EvidenceTransaction(source.sourceType().toLowerCase() + ":" + source.sourceId()
                + ("RECURRING_COMMITMENT".equals(source.sourceType()) && source.dueDate() != null ? ":" + source.dueDate() : ""), date,
                source.label(), source.category() + " · " + evidenceTag(source), component("Monthly amount", source.plannedAmount(), currency), source.detail());
    }

    private String evidenceTag(MonthlyFinancialSnapshotService.Source source) {
        if ("CREDIT_CARD_BILL".equals(source.sourceType())) return "Statement-period total";
        if ("RECURRING_COMMITMENT".equals(source.sourceType())) return source.detail() != null && source.detail().contains("recorded as set aside")
                ? source.detail() : "Recent-bill estimate".equals(source.detail()) ? "Recent-bill estimate" : "Scheduled payment";
        if (source.remainingPayments() == null) return "🌱 Future-you contribution";
        String end = monthLabel(YearMonth.parse(source.endsInMonth()));
        if (source.remainingPayments() <= 2) return "🚪 Final episode · exits " + end;
        if (source.remainingPayments() <= 6) return "⏳ Countdown mode · ends " + end;
        return "👾 Long-game member · ends " + end;
    }

    private String monthLabel(YearMonth month) {
        return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("en-IN"))
                + " " + month.getYear();
    }
}
