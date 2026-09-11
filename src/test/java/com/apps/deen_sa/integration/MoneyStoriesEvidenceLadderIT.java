package com.apps.deen_sa.integration;

import com.apps.deen_sa.normalization.ExpenseConfirmationPort;
import com.apps.deen_sa.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.normalization.ExpenseNormalizationPort;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import com.apps.deen_sa.service.ExpenseConfirmationCommandHandler;
import com.apps.deen_sa.service.ExpenseDailyAggregationService;
import com.apps.deen_sa.service.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.service.FinancialTransactionEditService;
import com.apps.deen_sa.service.MoneyStoriesService;
import com.apps.deen_sa.service.MoneyStoryCopyGenerator;
import com.apps.deen_sa.service.MoneyStoryGenerationScheduler;
import com.apps.deen_sa.service.TransactionDraftWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;

/** Red acceptance tests for the proposed evidence ladder; see docs/testing/money-stories-tdd-plan.md. */
@SpringBootTest(properties = {"openai.api-key=", "app.money-stories.generation-cron=-",
        "app.aggregation.scheduling-enabled=false"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class MoneyStoriesEvidenceLadderIT {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppUserRepository users;
    @Autowired private ExpenseDailyAggregationService dailyAggregation;
    @Autowired private MoneyStoryGenerationScheduler storyCron;
    @Autowired private MoneyStoriesService stories;
    @Autowired private TransactionDraftWriter drafts;
    @Autowired private ExpenseNormalizationHandler normalization;
    @Autowired private ExpenseConfirmationCommandHandler confirmation;
    @Autowired private TransactionDraftExtractionRepository extractions;
    @Autowired private ExpenseTaxonomyRegistry taxonomy;
    @Autowired private FinancialTransactionEditService edits;
    @MockBean private ExpenseNormalizationPort normalizer;
    @MockBean private ExpenseConfirmationPort confirmationPort;
    @MockBean private MoneyStoryCopyGenerator copy;
    @MockBean private Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();


    @ParameterizedTest(name = "60-day story journey: {0}")
    @ValueSource(strings = {"A", "B", "C"})
    void storiesReflectTheEvidenceAvailableOnEachDay(String persona) throws Exception {
        JsonNode fixture = loadSixtyDayFixture();
        JsonNode scenario = findUserScenario(fixture, persona);
        MoneyStoryScenarioDriver journey = new MoneyStoryScenarioDriver(
                jdbc, users, dailyAggregation, storyCron, stories, drafts, normalization,
                confirmation, extractions, taxonomy, edits, normalizer, copy, clock);
        MoneyStoryAssertions expectations = new MoneyStoryAssertions(jdbc);
        journey.startUser(persona, fixture.path("sourceAccount").asText());

        // A: occasional entries. B: partial monthly backfills. C: consistent daily records.
        for (JsonNode day : scenario.path("days")) {
            LocalDate date = LocalDate.parse(day.path("date").asText());
            journey.beginDay(date);
            journey.recordTodaysExpenses(day);
            journey.applyTodaysCorrections(day);
            journey.runEndOfDayJobs(date);

            verifyStoriesForToday(persona, day, date, journey, expectations);
        }
        expectations.assertPublishedEvidenceCoversVariedHouseholdExpenses(journey.owner(), persona);
        expectations.assertAllExpectations();
    }

    @Test
    void scheduledJobRebuildsHistoricalCorrectionsWithoutManualAggregation() throws Exception {
        JsonNode fixture = loadSixtyDayFixture();
        JsonNode scenario = findUserScenario(fixture, "A");
        MoneyStoryScenarioDriver journey = new MoneyStoryScenarioDriver(
                jdbc, users, dailyAggregation, storyCron, stories, drafts, normalization,
                confirmation, extractions, taxonomy, edits, normalizer, copy, clock);
        MoneyStoryAssertions expectations = new MoneyStoryAssertions(jdbc);
        journey.startUser("A", fixture.path("sourceAccount").asText());
        for (int index = 0; index < 6; index++) {
            JsonNode day = scenario.path("days").get(index);
            journey.beginDay(LocalDate.parse(day.path("date").asText()));
            journey.recordTodaysExpenses(day);
            journey.rerunStoryJob(); // The production worker must rebuild the recorded dates itself.
        }
        expectations.assertJulyRecordedTotalMatchesCheckpoint("A", 6, journey.owner());

        JsonNode correctionDay = scenario.path("days").get(7);
        LocalDate date = LocalDate.parse(correctionDay.path("date").asText());
        journey.beginDay(date);
        journey.applyTodaysCorrections(correctionDay);
        expectations.assertCorrectionMarksPublishedStoriesStale(journey.readStories(YearMonth.from(date)));
        journey.rerunStoryJob();
        expectations.assertWorkerPublishesFreshStories(journey.readStories(YearMonth.from(date)));
        verifyStoriesForToday("A", correctionDay, date, journey, expectations);
        expectations.assertAllExpectations();
    }

    private void verifyStoriesForToday(String persona, JsonNode day, LocalDate date,
            MoneyStoryScenarioDriver journey, MoneyStoryAssertions expectations) {
        if (!journey.hasRecordedExpenses()) return;
        for (YearMonth month : journey.recordedMonths()) {
            String label = persona + " day " + day.path("day").asInt() + " / " + month;
            var publishedStories = journey.readStories(month);
            expectations.assertMonthlyDeckIsSupportedByRecordedExpenses(persona, label, publishedStories,
                    journey.expectsObservationIn(month, date), date, journey.evaluationTime(),
                    journey.owner(), journey.recordedTransactionIds());

            journey.rerunStoryJob();
            expectations.assertUnchangedJobPreservesPublishedStories(label, publishedStories, journey.readStories(month));
        }
        expectations.assertJulyRecordedTotalMatchesCheckpoint(persona, day.path("day").asInt(), journey.owner());
    }

    private JsonNode loadSixtyDayFixture() throws Exception {
        try (var input = new ClassPathResource("money-stories/sixty-day-story-fixture.json").getInputStream()) {
            return mapper.readTree(input);
        }
    }

    private JsonNode findUserScenario(JsonNode fixture, String persona) {
        for (JsonNode scenario : fixture.path("users")) {
            if (scenario.path("user").asText().equals(persona)) {
                assertThat(scenario.path("days").size()).as("Each user journey covers 60 days").isEqualTo(60);
                return scenario;
            }
        }
        throw new IllegalArgumentException("Missing fixture scenario for user " + persona);
    }
}
