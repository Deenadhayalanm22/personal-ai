package com.apps.deen_sa.service;

import com.apps.deen_sa.config.MoneyStoriesProperties;
import com.apps.deen_sa.domain.MoneyStoryLevel;
import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.repository.MoneyStoryAggregateRepository.CategoryDay;
import com.apps.deen_sa.repository.MoneyStoryAggregateRepository.ReferenceDay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Tests each topic independently of feed ranking, using the reviewed day-by-day fixture. */
class MoneyStoryEvidenceRulesTest {
    private final ExpenseTaxonomyRegistry taxonomy = new ExpenseTaxonomyRegistry();
    private final MoneyStoriesProperties properties = new MoneyStoriesProperties(new MoneyStoriesProperties.Rules(
            new MoneyStoriesProperties.DiscretionaryFrequency(true, 3),
            new MoneyStoriesProperties.CategorySpendingGrowth(true, amount("1000"), amount("500"), amount("25")),
            new MoneyStoriesProperties.WeekendSpendingPattern(true, amount("1500"), amount("35")),
            new MoneyStoriesProperties.MerchantConcentration(true, amount("1500"), 3, amount("25")),
            new MoneyStoriesProperties.UnusualHighSpendDay(true, amount("2000"), amount("2.5"), 8, 15)));

    @ParameterizedTest(name = "{0}: sparse data supports an observation")
    @EnumSource(MoneyStoryType.class)
    void everyTopicOffersFactualObservationBeforePatternsAreSupported(MoneyStoryType topic) throws Exception {
        // Day 6 includes the first weekend, so all five topics have relevant facts.
        var candidate = rule(topic).evaluate(context("A", 6, YearMonth.of(2026, 7))).orElseThrow();
        assertThat(candidate.level()).isEqualTo(MoneyStoryLevel.OBSERVATION);
        assertThat(candidate.impact()).isPositive();
        assertThat(candidate.percentOrRatio()).as("Observation must not imply a historical comparison").isNull();
        assertThat(candidate.periodEnd()).isBeforeOrEqualTo(LocalDate.of(2026, 7, 11));
    }

    @ParameterizedTest(name = "{0}: sufficient history supports a pattern")
    @EnumSource(MoneyStoryType.class)
    void everyTopicCanBecomeAPatternWithItsRequiredEvidence(MoneyStoryType topic) throws Exception {
        int day = switch (topic) {
            case CATEGORY_SPENDING_GROWTH -> 60;
            case UNUSUAL_HIGH_SPEND_DAY -> 50;
            default -> 29;
        };
        var candidate = rule(topic).evaluate(context("C", day, YearMonth.of(2026, 8))).orElseThrow();
        assertThat(candidate.level()).isEqualTo(MoneyStoryLevel.PATTERN);
        switch (topic) {
            case CATEGORY_SPENDING_GROWTH -> {
                assertThat(candidate.impact()).isEqualByComparingTo("6850");
                assertThat(candidate.comparison()).isEqualByComparingTo("14800");
                assertThat(candidate.percentOrRatio()).isEqualByComparingTo("46.28");
            }
            case UNUSUAL_HIGH_SPEND_DAY -> {
                assertThat(candidate.impact()).isEqualByComparingTo("6650");
                assertThat(candidate.comparison()).isEqualByComparingTo("650");
                assertThat(candidate.percentOrRatio()).isEqualByComparingTo("10.23");
            }
            case WEEKEND_SPENDING_PATTERN -> {
                assertThat(candidate.impact()).isEqualByComparingTo("6400");
                assertThat(candidate.comparison()).isEqualByComparingTo("16400");
                assertThat(candidate.percentOrRatio()).isEqualByComparingTo("39.02");
            }
            case DISCRETIONARY_FREQUENCY -> {
                assertThat(candidate.impact()).isEqualByComparingTo("10800");
                assertThat(candidate.count()).isEqualTo(28);
            }
            case MERCHANT_CONCENTRATION -> {
                // Both merchants qualify; the agreed subject tie-break selects BigBasket.
                assertThat(candidate.category()).isEqualTo("BigBasket");
                assertThat(candidate.impact()).isEqualByComparingTo("5600");
                assertThat(candidate.count()).isEqualTo(28);
                assertThat(candidate.percentOrRatio()).isEqualByComparingTo("34.15");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MoneyStoryType.class)
    void partialMonthlyBackfillsDoNotQualifyAsPatterns(MoneyStoryType topic) throws Exception {
        rule(topic).evaluate(context("B", 60, YearMonth.of(2026, 8))).ifPresent(candidate ->
                assertThat(candidate.level()).isEqualTo(MoneyStoryLevel.OBSERVATION));
    }

    @Test
    void openMonthDoesNotQualifyForMonthlyGrowth() throws Exception {
        var candidate = rule(MoneyStoryType.CATEGORY_SPENDING_GROWTH)
                .evaluate(context("C", 56, YearMonth.of(2026, 8))).orElseThrow();
        assertThat(candidate.level()).isEqualTo(MoneyStoryLevel.OBSERVATION);
    }

    @Test
    void medianAveragesBothMiddleValuesForEvenSamples() {
        assertThat(MoneyStoryRuleSupport.median(List.of(amount("100"), amount("500"), amount("200"), amount("300"))))
                .isEqualByComparingTo("250");
    }

    @Test
    void movingLargeExpenseRemovesTheOriginalDayPattern() throws Exception {
        var candidate = rule(MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY)
                .evaluate(context("C", 59, YearMonth.of(2026, 8))).orElseThrow();
        assertThat(candidate.level()).isEqualTo(MoneyStoryLevel.OBSERVATION);
    }

    @Test
    void variedHouseholdSpendingDoesNotForceAMerchantConcentrationPattern() throws Exception {
        var household = contextFromFixture("sixty-day-story-fixture.json", "C", 60, YearMonth.of(2026, 8));
        var candidate = rule(MoneyStoryType.MERCHANT_CONCENTRATION).evaluate(household).orElseThrow();
        assertThat(candidate.level()).as("Spending spread across bills, transport and shops need not concentrate at one merchant")
                .isEqualTo(MoneyStoryLevel.OBSERVATION);
    }

    @Test
    void realisticHouseholdIncludesHomeTransportSchoolAndLeisure() throws Exception {
        var household = contextFromFixture("sixty-day-story-fixture.json", "C", 60, YearMonth.of(2026, 8));
        assertThat(household.current()).extracting(CategoryDay::category).contains(
                "Housing", "Utilities", "Transportation", "Education", "Family", "Food & Dining",
                "Travel", "Shopping", "Entertainment", "Insurance", "Personal Care");
        assertThat(household.current()).extracting(CategoryDay::nature).contains("ESSENTIAL", "FLEXIBLE", "DISCRETIONARY");
        assertThat(household.references().stream().filter(r -> r.referenceName().equals("Swiggy")))
                .as("Delivery is occasional, not the household's daily spending model").hasSize(3);
    }

    private MoneyStoryRule rule(MoneyStoryType topic) {
        return switch (topic) {
            case DISCRETIONARY_FREQUENCY -> new DiscretionaryFrequencyRule(properties);
            case CATEGORY_SPENDING_GROWTH -> new CategorySpendingGrowthRule(properties);
            case WEEKEND_SPENDING_PATTERN -> new WeekendSpendingPatternRule(properties);
            case MERCHANT_CONCENTRATION -> new MerchantConcentrationRule(properties);
            case UNUSUAL_HIGH_SPEND_DAY -> new UnusualHighSpendDayRule(properties);
        };
    }

    private StoryEvaluationContext context(String persona, int throughDay, YearMonth scope) throws Exception {
        return contextFromFixture("focused-rule-story-fixture.json", persona, throughDay, scope);
    }

    private StoryEvaluationContext contextFromFixture(String fixtureName, String persona, int throughDay, YearMonth scope) throws Exception {
        JsonNode fixture;
        try (var stream = new ClassPathResource("money-stories/" + fixtureName).getInputStream()) {
            fixture = new ObjectMapper().readTree(stream);
        }
        Map<String, JsonNode> recorded = new LinkedHashMap<>();
        for (JsonNode user : fixture.path("users")) {
            if (!user.path("user").asText().equals(persona)) continue;
            for (JsonNode day : user.path("days")) {
                if (day.path("day").asInt() > throughDay) break;
                for (JsonNode entry : day.path("entries")) recorded.put(entry.path("id").asText(), entry.deepCopy());
                for (JsonNode edit : day.path("edits")) {
                    String id = edit.path("transaction").asText();
                    switch (edit.path("operation").asText()) {
                        case "DELETE" -> recorded.remove(id);
                        case "MOVE" -> ((com.fasterxml.jackson.databind.node.ObjectNode) recorded.get(id)).put("occurredOn", edit.path("occurredOn").asText());
                        case "AMOUNT" -> ((com.fasterxml.jackson.databind.node.ObjectNode) recorded.get(id)).put("amount", edit.path("amount").asText());
                        default -> throw new IllegalArgumentException("Unknown fixture edit");
                    }
                }
            }
        }
        LocalDate today = LocalDate.parse(fixture.path("startDate").asText()).plusDays(throughDay - 1);
        List<CategoryDay> rows = new ArrayList<>();
        List<ReferenceDay> references = new ArrayList<>();
        Map<String, Long> merchantIds = new HashMap<>();
        for (JsonNode entry : recorded.values()) {
            LocalDate date = LocalDate.parse(entry.path("occurredOn").asText());
            String subcategory = entry.path("subcategory").asText(), merchant = entry.path("merchant").asText();
            BigDecimal amount = amount(entry.path("amount").asText());
            rows.add(new CategoryDay(date, entry.path("category").asText(), subcategory,
                    taxonomy.spendingNatureFor(entry.path("category").asText(), subcategory).orElseThrow().name(), amount, 1, Instant.EPOCH));
            long merchantId = merchantIds.computeIfAbsent(merchant, key -> (long) merchantIds.size() + 1);
            references.add(new ReferenceDay(date, merchantId, merchant, "MERCHANT", amount, 1, Instant.EPOCH));
        }
        LocalDate start = scope.atDay(1), end = scope.plusMonths(1).atDay(1);
        return new StoryEvaluationContext(scope, today, MoneyStoryRuleSupport.in(rows, start, end),
                MoneyStoryRuleSupport.in(rows, start.minusMonths(1), start),
                MoneyStoryRuleSupport.in(rows, start.minusWeeks(8), start),
                references.stream().filter(r -> !r.date().isBefore(start.minusWeeks(8)) && r.date().isBefore(end)).toList());
    }
    private static BigDecimal amount(String value) { return new BigDecimal(value); }
}
