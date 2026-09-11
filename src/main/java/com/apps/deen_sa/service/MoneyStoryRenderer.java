package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryLevel;
import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.entity.AppUserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.apps.deen_sa.service.MoneyStoriesService.*;

/** Presentation only: calculations and eligibility stay in the topic rules. */
@Component
@RequiredArgsConstructor
public class MoneyStoryRenderer {
    private final MoneyStoryCopyGenerator copyGenerator;

    public StoryDto render(MoneyStoryCandidate c, AppUserEntity user, String logicalId, int revision, Instant generatedAt) {
        String value = money(c.impact(), user.getCurrency());
        String period = label(c.periodStart(), c.periodEnd());
        boolean pattern = c.level() == MoneyStoryLevel.PATTERN;
        String subject = switch (c.type()) {
            case DISCRETIONARY_FREQUENCY -> "discretionary purchases";
            case CATEGORY_SPENDING_GROWTH -> c.category();
            case WEEKEND_SPENDING_PATTERN -> "weekend expenses";
            case MERCHANT_CONCENTRATION -> c.category();
            case UNUSUAL_HIGH_SPEND_DAY -> "daily expenses";
        };
        String title = "Your recorded " + subject;
        String body = "You recorded " + c.count() + " expenses totaling " + value + " during " + period + ".";
        String comparisonTitle = "Based on what you recorded";
        String comparisonBody = "This reflects the expenses you have shared for these dates.";
        if (pattern) {
            switch (c.type()) {
                case CATEGORY_SPENDING_GROWTH -> {
                    title = "Recorded " + subject + " increased";
                    body = "The recorded total increased by " + value + " during " + period + ".";
                    comparisonTitle = c.percentOrRatio().stripTrailingZeros().toPlainString() + "% above the previous month";
                    comparisonBody = "The previous month's recorded total was " + money(c.comparison(), user.getCurrency()) + ".";
                }
                case UNUSUAL_HIGH_SPEND_DAY -> {
                    title = "A recorded spending day stood out";
                    comparisonTitle = c.percentOrRatio().stripTrailingZeros().toPlainString() + "× your typical recorded day";
                    comparisonBody = "Compared with the median of your recorded spending days in the preceding eight weeks.";
                }
                case DISCRETIONARY_FREQUENCY -> {
                    comparisonTitle = "Recorded in each of four completed weeks";
                    comparisonBody = "Discretionary purchases appeared on multiple dates in every week.";
                }
                default -> {
                    comparisonTitle = c.percentOrRatio().stripTrailingZeros().toPlainString() + "% of recorded spending";
                    comparisonBody = "Based on four completed weeks with expenses recorded across multiple dates.";
                }
            }
        }
        var fallback = new MoneyStoryCopyGenerator.Copy("Recorded expenses", "calm", "CALM_CONTEXT",
                pattern ? "A recorded pattern" : "What you recorded", title, body, comparisonTitle, comparisonBody);
        // Observation wording is deliberately factual; no LLM inference from sparse evidence.
        var copy = pattern ? copyGenerator.generate(c.type(), Map.of("level", c.level().name(),
                "amount", value, "count", Integer.toString(c.count()), "subject", subject, "period", period,
                "comparison", comparisonTitle), fallback) : fallback;
        String periodType = c.type() == MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY ? "DAY"
                : pattern && c.type() != MoneyStoryType.CATEGORY_SPENDING_GROWTH ? "FOUR_WEEKS" : "MONTH";
        return new StoryDto(UUID.randomUUID().toString(), c.type().name(), 2, generatedAt,
                new PeriodDto(periodType, c.periodStart(), c.periodEnd(), period),
                new CardFace(copy.heading(), value, copy.faceTheme()), List.of(
                    new CardDto("recognition", 1, "HERO_STAT", copy.theme(), copy.eyebrow(), copy.title(), copy.body(),
                            List.of(new MoneyStoriesService.Component("MONEY", "Amount", c.impact(), user.getCurrency(), value)), List.of()),
                    new CardDto("comparison", 2, "TWO_STAT_COMPARISON", "CALM_CONTEXT", "Put it in context",
                            copy.comparisonTitle(), copy.comparisonBody(), List.of(), List.of()),
                    new CardDto("action", 3, "REFLECTION_ACTION", "POSITIVE_ACTION", "Look a little closer",
                            "Review the recorded expenses", "See the exact expenses included in this story.", List.of(),
                            List.of(new Action("OPEN_EVIDENCE", "Review expenses")))),
                null, c.level(), logicalId, revision, revision > 1 ? "Recorded expenses or pattern evidence changed" : null);
    }

    static String money(BigDecimal amount, String currency) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        formatter.setCurrency(Currency.getInstance(currency));
        return formatter.format(amount);
    }
    static String label(LocalDate start, LocalDate end) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("d MMM uuuu");
        return start.equals(end) ? start.format(format) : start.format(format) + "–" + end.format(format);
    }
}
