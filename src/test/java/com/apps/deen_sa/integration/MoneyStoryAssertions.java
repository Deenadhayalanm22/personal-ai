package com.apps.deen_sa.integration;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.service.MoneyStoriesService.MoneyStoriesResponse;
import com.apps.deen_sa.service.MoneyStoriesService.StoryDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.SoftAssertions;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Read-only expectations. Collect failures so the replay can reach all 60 days. */
final class MoneyStoryAssertions {
    private final SoftAssertions checks = new SoftAssertions();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final JdbcTemplate jdbc;

    MoneyStoryAssertions(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void assertMonthlyDeckIsSupportedByRecordedExpenses(String persona, String label,
            MoneyStoriesResponse response, boolean expectObservation, LocalDate date,
            Instant evaluatedAt, AppUserEntity owner, Collection<Long> transactionIds) {
        assertDeckContainsAtMostThreeStories(label, response);
        if (expectObservation) assertChangedPeriodHasUsefulObservation(label, response);
        Set<String> logicalKeys = new HashSet<>();
        Set<String> observationSubjects = new HashSet<>();
        for (StoryDto story : response.stories()) {
            assertStoryHasSupportedLevel(persona, label, story);
            assertObservationIsOneGroundedCard(label, story);
            if (story.observation() != null) checks.assertThat(observationSubjects.add(story.observation().noveltyKey()))
                    .as(label + " / observations explain distinct subjects").isTrue();
            assertPersistedLevelMatchesPublishedStory(label, story);
            assertStoryHasIdentityAndIsNotDuplicated(label, story, logicalKeys);
            assertStoryDoesNotDescribeTheFuture(label, story, date, evaluatedAt);
            assertEvidenceBelongsToUserAndTotalsReconcile(label, story, owner, transactionIds);
        }
    }

    private void assertObservationIsOneGroundedCard(String label, StoryDto story) {
        if (story.level() != com.apps.deen_sa.domain.MoneyStoryLevel.OBSERVATION) return;
        checks.assertThat(story.cards()).as(label + " / an observation needs only one card").hasSize(1);
        checks.assertThat(story.observation()).as(label + " / observation exposes its supporting facts").isNotNull();
        if (story.observation() == null) return;
        var facts = story.observation();
        var evidence = mapper.valueToTree(story.evidence());
        checks.assertThat(facts.total()).as(label + " / observation total agrees with its evidence")
                .isEqualByComparingTo(evidence.path("totalAmount").path("value").decimalValue());
        checks.assertThat(facts.focusAmount().add(facts.otherAmount())).as(label + " / explanation accounts for the whole amount")
                .isEqualByComparingTo(facts.total());
        checks.assertThat(facts.parts().stream().map(com.apps.deen_sa.service.MoneyStoryObservation.Part::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)).as(label + " / subcategory breakdown reconciles")
                .isEqualByComparingTo(facts.total());
        checks.assertThat(facts.evidenceIds()).as(label + " / every included expense supports the explanation")
                .hasSize(evidence.path("totalCount").asInt());
        story.cards().forEach(card -> {
            checks.assertThat(card.actions()).as(label + " / evidence opens directly from the observation")
                    .extracting(com.apps.deen_sa.service.MoneyStoriesService.Action::type).contains("OPEN_EVIDENCE");
            checks.assertThat(card.body()).as(label + " / no empty comparison filler")
                    .doesNotContain("This reflects the expenses you have shared for these dates.");
        });
    }

    private void assertDeckContainsAtMostThreeStories(String label, MoneyStoriesResponse response) {
        checks.assertThat(response.stories().size()).as(label + " / deck contains at most three stories").isLessThanOrEqualTo(3);
    }

    private void assertChangedPeriodHasUsefulObservation(String label, MoneyStoriesResponse response) {
        checks.assertThat(response.stories()).as(label + " / changed period has a useful factual fallback").isNotEmpty();
    }

    private void assertStoryHasSupportedLevel(String persona, String label, StoryDto story) {
        String level = mapper.valueToTree(story).path("level").asText();
        checks.assertThat(level).as(label + " / MVP supports observation or pattern").isIn("OBSERVATION", "PATTERN");
        if (!persona.equals("C")) {
            checks.assertThat(level).as(label + " / sparse fixture supports observations only").isEqualTo("OBSERVATION");
        }
    }

    private void assertPersistedLevelMatchesPublishedStory(String label, StoryDto story) {
        String persistedLevel = jdbc.queryForObject(
                "SELECT story_level FROM money_story WHERE id=?", String.class,
                java.util.UUID.fromString(story.storyId()));
        checks.assertThat(persistedLevel).as(label + " / dedicated story_level column matches the published level")
                .isEqualTo(story.level().name());
    }

    private void assertStoryHasIdentityAndIsNotDuplicated(String label, StoryDto story, Set<String> keys) {
        String logicalId = mapper.valueToTree(story).path("logicalStoryId").asText();
        checks.assertThat(logicalId).as(label + " / story has a stable logical identity").isNotBlank();
        checks.assertThat(keys.add(story.storyType() + ":" + logicalId))
                .as(label + " / logical story appears only once").isTrue();
    }

    private void assertStoryDoesNotDescribeTheFuture(String label, StoryDto story, LocalDate date, Instant evaluatedAt) {
        checks.assertThat(story.period().endDate()).as(label + " / period excludes future dates").isBeforeOrEqualTo(date);
        checks.assertThat(story.generatedAt()).as(label + " / generation respects the simulated clock").isBeforeOrEqualTo(evaluatedAt);
    }

    private void assertEvidenceBelongsToUserAndTotalsReconcile(String label, StoryDto story,
            AppUserEntity owner, Collection<Long> transactionIds) {
        JsonNode evidence = mapper.valueToTree(story.evidence());
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode row : evidence.path("transactions")) {
            long id = Long.parseLong(row.path("transactionId").asText());
            checks.assertThat(transactionIds).as(label + " / evidence belongs to this scenario user").contains(id);
            Integer visible = jdbc.queryForObject("""
                    SELECT count(*) FROM financial_transaction
                    WHERE id=? AND user_id=? AND deleted_at IS NULL
                    """, Integer.class, id, owner.getId());
            checks.assertThat(visible).as(label + " / evidence excludes deleted expenses").isEqualTo(1);
            total = total.add(row.path("amount").path("value").decimalValue());
        }
        checks.assertThat(evidence.path("totalAmount").path("value").decimalValue())
                .as(label + " / evidence amounts add up to the displayed total").isEqualByComparingTo(total);
    }

    void assertUnchangedJobPreservesPublishedStories(String label, MoneyStoriesResponse before, MoneyStoriesResponse after) {
        checks.assertThat(after).as(label + " / rerunning without changes preserves published stories").isEqualTo(before);
    }

    void assertJulyRecordedTotalMatchesCheckpoint(String persona, int day, AppUserEntity owner) {
        String expected = null;
        if (persona.equals("A")) expected = switch (day) {
            case 6 -> "1500"; // Groceries, bike fuel and a movie.
            case 8 -> "1200"; // One amount corrected from 500 to 200.
            case 9 -> "700";  // Duplicate movie purchase deleted.
            case 26 -> "2500"; // Family trip accommodation added.
            default -> null;
        };
        if (persona.equals("B") && day == 26) expected = "44699"; // Partial July bills and outings backfill.
        if (persona.equals("C") && day == 60) expected = "77808"; // July household spending, including the corrected car-repair date.
        if (expected == null) return;
        BigDecimal total = jdbc.queryForObject("""
                SELECT coalesce(sum(amount),0) FROM financial_transaction
                WHERE user_id=? AND deleted_at IS NULL
                  AND occurred_at >= DATE '2026-07-01' AND occurred_at < DATE '2026-08-01'
                """, BigDecimal.class, owner.getId());
        checks.assertThat(total).as(persona + " day " + day + " / July household ledger matches the independent checkpoint")
                .isEqualByComparingTo(expected);
    }

    void assertCorrectionMarksPublishedStoriesStale(MoneyStoriesResponse response) {
        checks.assertThat(response.stale()).as("An old expense correction marks its published snapshot stale").isTrue();
    }

    void assertWorkerPublishesFreshStories(MoneyStoriesResponse response) {
        checks.assertThat(response.stale()).as("The worker rebuilds old projections before publishing fresh stories").isFalse();
        checks.assertThat(response.stories()).as("Corrected expenses still support an observation").isNotEmpty();
    }

    void assertPublishedEvidenceCoversVariedHouseholdExpenses(AppUserEntity owner, String persona) {
        var categories = jdbc.queryForList("""
                SELECT DISTINCT evidence.category_label FROM money_story_evidence evidence
                JOIN money_story story ON story.id=evidence.story_id
                JOIN money_story_snapshot snapshot ON snapshot.id=story.snapshot_id
                WHERE snapshot.user_id=?
                """, String.class, owner.getId());
        checks.assertThat(categories).as(persona + " / published stories draw evidence from multiple household domains")
                .hasSizeGreaterThanOrEqualTo(persona.equals("C") ? 6 : 4);
        if (persona.equals("C")) checks.assertThat(categories)
                .as("The regular user's published evidence includes rent, commuting and family expenses")
                .contains("Housing", "Transportation", "Education", "Food & Dining");
    }

    void assertSeptemberCardsExplainTheRecordedExpenses(com.apps.deen_sa.service.MoneyStoriesService.MonthlyStoriesApiResponse response) {
        checks.assertThat(response.stories()).as("The September example publishes three complementary observations").hasSize(3);
        var daily = response.stories().stream().filter(s -> s.storyType().equals("UNUSUAL_HIGH_SPEND_DAY")
                && s.period().startDate().equals(LocalDate.of(2026, 9, 10))).findFirst();
        checks.assertThat(daily).as("The concrete September 10 explanation is selected").isPresent();
        daily.ifPresent(story -> {
            checks.assertThat(story.observation().focusAmount()).isEqualByComparingTo("5445");
            checks.assertThat(story.observation().otherAmount()).isEqualByComparingTo("662");
            checks.assertThat(story.cards()).hasSize(1);
            checks.assertThat(story.cards().getFirst().body()).contains("Amazon", "₹662");
            checks.assertThat(story.evidence().transactions()).extracting(com.apps.deen_sa.service.MoneyStoriesService.EvidenceTransaction::subcategoryLabel)
                    .contains("Household Items");
        });
        var food = response.stories().stream().filter(s -> s.observation() != null
                && s.observation().subject().equals("Groceries & provisions")).findFirst();
        checks.assertThat(food).as("Food composition remains visible alongside the purchase explanation").isPresent();
        food.ifPresent(story -> {
            checks.assertThat(story.observation().total()).isEqualByComparingTo("16203");
            checks.assertThat(story.observation().focusAmount()).isEqualByComparingTo("13476");
            checks.assertThat(story.observation().incompleteClassificationCount()).isEqualTo(2);
            checks.assertThat(story.evidence().totalAmount().value()).isEqualByComparingTo("16203");
        });
        response.stories().forEach(story -> {
            checks.assertThat(story.level()).isEqualTo(com.apps.deen_sa.domain.MoneyStoryLevel.OBSERVATION);
            checks.assertThat(story.logicalStoryId()).isNotBlank();
            checks.assertThat(story.revision()).isPositive();
        });
    }

    void assertAllExpectations() { checks.assertAll(); }
}
