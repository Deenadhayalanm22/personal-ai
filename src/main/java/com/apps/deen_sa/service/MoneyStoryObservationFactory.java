package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.FinancialTransactionEntity;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.apps.deen_sa.service.MoneyStoryObservation.Kind.*;
import static com.apps.deen_sa.service.MoneyStoryRuleSupport.percent;

/** Describes relationships within recorded expenses, without requiring historical coverage. */
@Component
public class MoneyStoryObservationFactory {
    public List<MoneyStoryCandidate> create(YearMonth month, LocalDate today, List<FinancialTransactionEntity> expenses) {
        var current = expenses.stream().filter(tx -> tx.getDeletedAt() == null && tx.getAmount() != null
                        && tx.getAmount().signum() > 0 && tx.getOccurredAt() != null
                        && YearMonth.from(tx.getOccurredAt()).equals(month) && !tx.getOccurredAt().isAfter(today))
                .sorted(Comparator.comparing(FinancialTransactionEntity::getOccurredAt).thenComparing(FinancialTransactionEntity::getId)).toList();
        List<MoneyStoryCandidate> result = new ArrayList<>();
        for (var group : group(current, tx -> text(tx.getCategory(), "Unclassified expenses")).entrySet()) {
            result.add(candidate(MoneyStoryType.CATEGORY_SPENDING_GROWTH, month, group.getValue(), group.getKey(), null, null));
        }
        for (var rows : group(current, tx -> tx.getOccurredAt().toString()).values()) {
            result.add(candidate(MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY, month, rows, null, null, null));
        }
        var discretionary = current.stream().filter(tx -> tx.getSpendingNature() == SpendingNature.DISCRETIONARY).toList();
        if (!discretionary.isEmpty()) {
            var c = candidate(MoneyStoryType.DISCRETIONARY_FREQUENCY, month, discretionary, null, SpendingNature.DISCRETIONARY, null);
            // Scope of this particular observation excludes entries whose nature has not been classified.
            var o = c.observation();
            int missing = (int) current.stream().filter(tx -> tx.getSpendingNature() == null).count();
            var scoped = new MoneyStoryObservation(o.kind(), o.subject(), o.merchantLabel(), o.total(), o.focusAmount(),
                    o.otherAmount(), o.sharePercent(), o.count(), o.focusCount(), o.activeDays(), o.parts(), o.evidenceIds(),
                    o.focusTransactionIds(), Math.max(missing, o.incompleteClassificationCount()), o.possibleRepeatCount(), o.noveltyKey());
            result.add(new MoneyStoryCandidate(c.type(), c.level(), c.periodStart(), c.periodEnd(), c.impact(), c.count(),
                    c.comparison(), c.category(), c.percentOrRatio(), c.nature(), c.merchantId(), c.evidenceStart(), c.evidenceEnd(), scoped));
        }
        var weekends = current.stream().filter(tx -> MoneyStoryRuleSupport.weekend(tx.getOccurredAt())).toList();
        if (!weekends.isEmpty()) result.add(candidate(MoneyStoryType.WEEKEND_SPENDING_PATTERN, month, weekends, null, null, null));
        for (var rows : group(current.stream().filter(tx -> tx.getMerchant() != null).toList(), tx -> tx.getMerchant().getId().toString()).values()) {
            result.add(candidate(MoneyStoryType.MERCHANT_CONCENTRATION, month, rows,
                    rows.getFirst().getMerchant().getCanonicalName(), null, rows.getFirst().getMerchant().getId()));
        }
        return result;
    }

    private MoneyStoryCandidate candidate(MoneyStoryType type, YearMonth month, List<FinancialTransactionEntity> rows,
            String category, SpendingNature nature, Long merchantId) {
        LocalDate first = rows.getFirst().getOccurredAt(), last = rows.getLast().getOccurredAt();
        LocalDate start = type == MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY ? first : month.atDay(1);
        var observation = describe(type, category, rows);
        return new MoneyStoryCandidate(type, MoneyStoryLevel.OBSERVATION, start, last, observation.total(), rows.size(),
                BigDecimal.ZERO, category, null, nature, merchantId, start, last.plusDays(1), observation);
    }

    private MoneyStoryObservation describe(MoneyStoryType type, String category, List<FinancialTransactionEntity> rows) {
        BigDecimal total = sum(rows);
        var grouped = group(rows, MoneyStoryObservationFactory::purpose);
        var parts = grouped.entrySet().stream().map(e -> new MoneyStoryObservation.Part(e.getKey(), sum(e.getValue()), e.getValue().size()))
                .sorted(Comparator.comparing(MoneyStoryObservation.Part::amount).reversed().thenComparing(MoneyStoryObservation.Part::label)).toList();
        int activeDays = (int) rows.stream().map(FinancialTransactionEntity::getOccurredAt).distinct().count();
        var largest = rows.stream().max(Comparator.comparing(FinancialTransactionEntity::getAmount)
                .thenComparing(FinancialTransactionEntity::getId)).orElseThrow();
        var kind = SUMMARY;
        String subject = text(category, "expenses");
        String merchantLabel = null;
        List<FinancialTransactionEntity> focus = rows;
        String novelty = "summary:" + type + ":" + category;
        if (type == MoneyStoryType.MERCHANT_CONCENTRATION && rows.size() >= 2 && activeDays >= 2) {
            kind = REPEAT; subject = category; merchantLabel = category;
            novelty = "merchant:" + rows.getFirst().getMerchant().getId();
        } else if ((type == MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY || type == MoneyStoryType.DISCRETIONARY_FREQUENCY
                || type == MoneyStoryType.WEEKEND_SPENDING_PATTERN)
                && rows.size() > 1 && percent(largest.getAmount(), total).compareTo(BigDecimal.valueOf(60)) >= 0) {
            kind = CONTRIBUTOR; subject = text(largest.getSubcategory(), "purchase"); focus = List.of(largest);
            merchantLabel = largest.getMerchant() == null ? null : largest.getMerchant().getCanonicalName();
            novelty = "contributor:" + (largest.getMerchant() == null ? "transaction:" + largest.getId() : "merchant:" + largest.getMerchant().getId());
        } else if (type == MoneyStoryType.CATEGORY_SPENDING_GROWTH && parts.size() > 1) {
            kind = BREAKDOWN; subject = parts.getFirst().label(); focus = grouped.get(subject);
            novelty = "breakdown:" + category;
        } else if (parts.size() == 1 && !parts.getFirst().label().equals("Unclassified expenses")
                && type == MoneyStoryType.CATEGORY_SPENDING_GROWTH) {
            kind = PURPOSE; subject = parts.getFirst().label(); novelty = "purpose:" + subject;
        } else if (type == MoneyStoryType.DISCRETIONARY_FREQUENCY || type == MoneyStoryType.WEEKEND_SPENDING_PATTERN) {
            // A useful breakdown remains possible even when no single purchase dominates.
            if (parts.size() > 1) { kind = BREAKDOWN; subject = parts.getFirst().label(); focus = grouped.get(subject); novelty = "breakdown:" + subject; }
        }
        BigDecimal focusAmount = sum(focus);
        int incomplete = (int) rows.stream().filter(tx -> tx.getSpendingNature() == null || blank(tx.getCategory()) || blank(tx.getSubcategory())).count();
        // This is a review hint, not a deduplication decision. Preserve every confirmed entry.
        int possibleRepeats = group(rows, tx -> tx.getOccurredAt() + ":" + tx.getAmount().stripTrailingZeros()
                + ":" + tx.getCategory() + ":" + tx.getSubcategory() + ":" + (tx.getMerchant() == null ? "" : tx.getMerchant().getId()))
                .values().stream().filter(group -> group.size() > 1).mapToInt(List::size).sum();
        return new MoneyStoryObservation(kind, subject, merchantLabel, total, focusAmount, total.subtract(focusAmount),
                percent(focusAmount, total), rows.size(), focus.size(), activeDays, parts,
                rows.stream().map(FinancialTransactionEntity::getId).toList(), focus.stream().map(FinancialTransactionEntity::getId).toList(),
                incomplete, possibleRepeats, novelty);
    }

    private static String purpose(FinancialTransactionEntity tx) {
        if ("Food & Dining".equals(tx.getCategory()) && Set.of("Groceries", "Rice, Grains & Provisions").contains(text(tx.getSubcategory(), ""))) {
            return "Groceries & provisions";
        }
        return text(tx.getSubcategory(), "Unclassified expenses");
    }
    private static Map<String, List<FinancialTransactionEntity>> group(List<FinancialTransactionEntity> rows,
            Function<FinancialTransactionEntity, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
    }
    private static BigDecimal sum(List<FinancialTransactionEntity> rows) {
        return rows.stream().map(FinancialTransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(String value, String fallback) { return blank(value) ? fallback : value; }
}
