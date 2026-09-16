package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryLevel;
import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.entity.AppUserEntity;
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
public class MonthlyCommitmentStoryService {
    private final MonthlyFinancialSnapshotService snapshots;
    private final MoneyStoryCopyGenerator copyGenerator;
    private final Clock clock;
    private final StoryEnrichmentPipeline enrichment;

    /** Retained for focused tests that exercise the commitment core without optional contributors. */
    public MonthlyCommitmentStoryService(MonthlyFinancialSnapshotService snapshots, MoneyStoryCopyGenerator copyGenerator, Clock clock) {
        this(snapshots, copyGenerator, clock, new StoryEnrichmentPipeline(List.of(), List.of()));
    }

    @Autowired
    public MonthlyCommitmentStoryService(MonthlyFinancialSnapshotService snapshots, MoneyStoryCopyGenerator copyGenerator, Clock clock,
                                         StoryEnrichmentPipeline enrichment) {
        this.snapshots = snapshots; this.copyGenerator = copyGenerator; this.clock = clock; this.enrichment = enrichment;
    }

    @Transactional(readOnly = true)
    public MoneyStoriesService.MoneyStoryApi currentFor(AppUserEntity user) {
        var snapshot = snapshots.current(user);
        var nextSnapshot = snapshots.next(user);
        YearMonth month = YearMonth.parse(snapshot.month());
        var debt = snapshot.commitmentBuckets().stream().filter(bucket -> "DEBT_REPAYMENTS".equals(bucket.key())).findFirst().orElseThrow();
        var investing = snapshot.commitmentBuckets().stream().filter(bucket -> "PLANNED_INVESTING".equals(bucket.key())).findFirst().orElseThrow();
        BigDecimal emiTotal = debt.plannedAmount(), sipTotal = investing.plannedAmount(), total = snapshot.fullIntendedCommitment();
        String currency = user.getCurrency();
        String value = MoneyStoryRenderer.money(total, currency);
        List<MoneyStoriesService.Component> components = new ArrayList<>();
        if (emiTotal.signum() > 0) components.add(component("Debt repayments", emiTotal, currency));
        if (sipTotal.signum() > 0) components.add(component("Planned investing", sipTotal, currency));

        String title = total.signum() > 0 ? "Your known monthly commitment" : "Your monthly commitment starts here";
        String body = total.signum() > 0
                ? commitmentBody(value, emiTotal, sipTotal, currency)
                : "Add a loan or a mutual fund SIP and it will appear here as part of your monthly commitment.";
        String payoffFacts = debt.sources().stream().filter(source -> source.remainingPayments() != null)
                .map(source -> source.label() + ": " + source.remainingPayments() + " payments remaining, ends " + source.endsInMonth()
                        + ", frees " + MoneyStoryRenderer.money(source.plannedAmount(), currency) + "/month from " + source.freesFromMonth())
                .collect(Collectors.joining("; "));
        var soonest = debt.sources().stream().filter(source -> source.remainingPayments() != null)
                .min(java.util.Comparator.comparing(MonthlyFinancialSnapshotService.Source::remainingPayments)).orElse(null);
        String fallbackTitle = soonest == null ? "Your money squad" : soonest.label() + " exits next";
        String fallbackBody = soonest == null ? body : MoneyStoryRenderer.money(soonest.plannedAmount(), currency)
                + "/month is free from " + monthLabel(YearMonth.parse(soonest.freesFromMonth())) + ".";
        var fallback = new MoneyStoryCopyGenerator.Copy("🎬 Payment squad", "warm", "WARM_NOTICE",
                monthLabel(month) + " · One update", fallbackTitle, fallbackBody, "", "");
        var nextFallback = nextFallback(nextSnapshot, currency);
        Map<String, String> copyFacts = new HashMap<>(Map.of("month", monthLabel(month), "total", value,
                "debtRepayments", MoneyStoryRenderer.money(emiTotal, currency), "plannedInvesting", MoneyStoryRenderer.money(sipTotal, currency),
                "loanPayoffFacts", payoffFacts, "soonestPayoff", soonest == null ? "None" : soonest.label() + " frees "
                        + MoneyStoryRenderer.money(soonest.plannedAmount(), currency) + "/month from " + soonest.freesFromMonth()));
        copyFacts.put("nextMonth", monthLabel(YearMonth.parse(nextSnapshot.month())));
        copyFacts.put("nextTotal", MoneyStoryRenderer.money(nextSnapshot.fullIntendedCommitment(), currency));
        copyFacts.put("nextDebtRepayments", bucketAmount(nextSnapshot, "DEBT_REPAYMENTS", currency));
        copyFacts.put("nextPlannedInvesting", bucketAmount(nextSnapshot, "PLANNED_INVESTING", currency));
        var runwayCopy = copyGenerator.generateCommitmentRunway(copyFacts,
                new MoneyStoryCopyGenerator.CommitmentRunwayCopy(fallback, nextFallback));
        var copy = runwayCopy.current();
        List<MoneyStoriesService.EvidenceTransaction> evidenceRows = evidenceRows(snapshot, currency);
        List<MoneyStoriesService.Action> actions = evidenceRows.isEmpty() ? List.of()
                : List.of(new MoneyStoriesService.Action("OPEN_EVIDENCE", "View included commitments"));
        var card = new MoneyStoriesService.CardDto("commitment", 1, "COMMITMENT", copy.theme(),
                copy.eyebrow(), copy.title(), copy.body(), List.copyOf(components), actions);
        var nextCard = nextMonthCard(nextSnapshot, currency, runwayCopy.next());
        StoryContext context = enrichment.enrich(new StoryEnrichmentRequest(user, MoneyStoryType.MONTHLY_COMMITMENT, month,
                Map.of("commitment.total", total)));
        List<MoneyStoriesService.CardDto> cards = new ArrayList<>(List.of(card, nextCard));
        context.insights().forEach(insight -> cards.add(insightCard(insight, cards.size() + 1, value)));
        if (context.insights().isEmpty() && total.signum() > 0) {
            cards.add(new MoneyStoriesService.CardDto("commitment-context", cards.size() + 1, "CONTEXT", "CALM_CONTEXT",
                    "OPTIONAL · PRIVATE", "Want a clearer monthly view?", "Add a salary range to see a private affordability lens for your commitments. "
                    + "It never changes your balance or records an income transaction.", List.of(),
                    List.of(new MoneyStoriesService.Action("OPEN_SALARY_OUTLOOK", "Add salary context"))));
        }
        var period = new MoneyStoriesService.PeriodDto("MONTH", month.atDay(1), month.atEndOfMonth(), monthLabel(month));
        var face = new MoneyStoriesService.CardFace(copy.heading(), value, copy.faceTheme());
        var evidence = new MoneyStoriesService.EvidenceDto("Commitment sources for the selected month", evidenceRows.size(),
                component("Monthly total", total, currency), List.of(), List.of(), Map.of(
                "commitment", evidence(snapshot, currency), "next-commitment", evidence(nextSnapshot, currency)));
        return new MoneyStoriesService.MoneyStoryApi("monthly-commitment", MoneyStoryType.MONTHLY_COMMITMENT.name(), 2,
                Instant.now(clock), period, face, List.copyOf(cards), evidence, MoneyStoryLevel.OBSERVATION,
                "monthly-commitment", 1, null, null);
    }

    private MoneyStoriesService.CardDto insightCard(StoryInsight insight, int sequence, String commitmentTotal) {
        if ("COMMITMENT_INCOME_EXACT".equals(insight.key())) {
            BigDecimal percent = (BigDecimal) insight.facts().get("percent");
            return new MoneyStoriesService.CardDto("commitment-income", sequence, "CONTEXT", "CALM_CONTEXT", "PRIVATE SALARY CONTEXT",
                    "Your commitments have a clear monthly frame", commitmentTotal + " is " + percent.stripTrailingZeros().toPlainString()
                    + "% of the monthly salary amount you chose to share.", List.of(), List.of());
        }
        String range = salaryRangeLabel((String) insight.facts().get("range"));
        return new MoneyStoriesService.CardDto("commitment-income", sequence, "CONTEXT", "CALM_CONTEXT", "PRIVATE SALARY CONTEXT",
                "Your commitments, with context", "You chose to share a salary range of " + range + ". Your known commitments are "
                + commitmentTotal + ". This range is private and does not change your balance.", List.of(), List.of());
    }

    private String salaryRangeLabel(String range) {
        return switch (range) {
            case "UNDER_25000" -> "under ₹25,000";
            case "FROM_25000_TO_50000" -> "₹25,000–₹50,000";
            case "FROM_50000_TO_100000" -> "₹50,000–₹1,00,000";
            case "FROM_100000_TO_200000" -> "₹1,00,000–₹2,00,000";
            case "OVER_200000" -> "over ₹2,00,000";
            default -> "the range you chose";
        };
    }

    private MoneyStoriesService.CardDto nextMonthCard(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency,
                                                        MoneyStoryCopyGenerator.Copy copy) {
        List<MoneyStoriesService.EvidenceTransaction> rows = evidenceRows(snapshot, currency);
        List<MoneyStoriesService.Action> actions = rows.isEmpty() ? List.of()
                : List.of(new MoneyStoriesService.Action("OPEN_EVIDENCE", "View " + monthLabel(YearMonth.parse(snapshot.month())) + " commitments"));
        return new MoneyStoriesService.CardDto("next-commitment", 2, "COMMITMENT", copy.theme(),
                copy.eyebrow(), copy.title(), copy.body(), components(snapshot, currency), actions);
    }

    private MoneyStoryCopyGenerator.Copy nextFallback(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        YearMonth month = YearMonth.parse(snapshot.month());
        String total = MoneyStoryRenderer.money(snapshot.fullIntendedCommitment(), currency);
        String title = snapshot.fullIntendedCommitment().signum() > 0 ? "Next month is queued" : "A clear runway ahead";
        String body = snapshot.fullIntendedCommitment().signum() > 0
                ? total + " is already planned for " + monthLabel(month) + ". Keep it ready before the month begins."
                : "No known loan EMI or SIP is planned for " + monthLabel(month) + " yet.";
        return new MoneyStoryCopyGenerator.Copy("🎬 Payment squad", "warm", "WARM_NOTICE",
                "NEXT MONTH · READY", title, body, "", "");
    }

    private String bucketAmount(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String key, String currency) {
        return snapshot.commitmentBuckets().stream().filter(bucket -> key.equals(bucket.key())).findFirst()
                .map(bucket -> MoneyStoryRenderer.money(bucket.plannedAmount(), currency)).orElse(MoneyStoryRenderer.money(BigDecimal.ZERO, currency));
    }

    private List<MoneyStoriesService.Component> components(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        List<MoneyStoriesService.Component> components = new ArrayList<>();
        snapshot.commitmentBuckets().forEach(bucket -> {
            if (bucket.plannedAmount().signum() > 0) components.add(component(bucket.label(), bucket.plannedAmount(), currency));
        });
        return List.copyOf(components);
    }

    private MoneyStoriesService.EvidenceDto evidence(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        List<MoneyStoriesService.EvidenceTransaction> rows = evidenceRows(snapshot, currency);
        return new MoneyStoriesService.EvidenceDto("Commitment sources by bucket", rows.size(),
                component("Monthly total", snapshot.fullIntendedCommitment(), currency), rows);
    }

    private List<MoneyStoriesService.EvidenceTransaction> evidenceRows(MonthlyFinancialSnapshotService.MonthlySnapshot snapshot, String currency) {
        return snapshot.commitmentBuckets().stream().flatMap(bucket -> bucket.sources().stream())
                .map(source -> evidence(source, currency)).toList();
    }

    private MoneyStoriesService.Component component(String label, BigDecimal amount, String currency) {
        return new MoneyStoriesService.Component("MONEY", label, amount, currency, MoneyStoryRenderer.money(amount, currency));
    }

    private String commitmentBody(String total, BigDecimal debt, BigDecimal investing, String currency) {
        if (debt.signum() > 0 && investing.signum() > 0) {
            return "Your full intended commitment is " + total + ": "
                    + MoneyStoryRenderer.money(debt, currency) + " in debt repayments and "
                    + MoneyStoryRenderer.money(investing, currency) + " in planned investing.";
        }
        if (debt.signum() > 0) return "Your required debt repayments total " + total + " this month.";
        return "Your planned investing total is " + total + " this month.";
    }

    private MoneyStoriesService.EvidenceTransaction evidence(MonthlyFinancialSnapshotService.Source source, String currency) {
        String date = source.dueDate() == null ? "Every month" : source.dueDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM"));
        return new MoneyStoriesService.EvidenceTransaction(source.sourceType().toLowerCase() + ":" + source.sourceId(), date,
                source.label(), source.category() + " · " + evidenceTag(source), component("Monthly amount", source.plannedAmount(), currency), source.detail());
    }

    private String evidenceTag(MonthlyFinancialSnapshotService.Source source) {
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
