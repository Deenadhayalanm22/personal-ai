package com.apps.deen_sa.integration;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.AppUserRepository;
import com.apps.deen_sa.repository.TransactionDraftExtractionRepository;
import com.apps.deen_sa.service.TransactionDraftWriter;
import com.apps.deen_sa.service.ExpenseConfirmationCommandHandler;
import com.apps.deen_sa.normalization.ExpenseNormalizationHandler;
import com.apps.deen_sa.normalization.ExpenseNormalizationPort;
import com.apps.deen_sa.normalization.ExpenseConfirmationPort;
import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.service.ExpenseDailyAggregationService;
import com.apps.deen_sa.service.MoneyStoriesService;
import com.apps.deen_sa.service.MoneyStoryGenerationScheduler;
import com.apps.deen_sa.service.ExpenseTaxonomyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Exercises the production capture path with normalization mocked: drafts, extractions, references, aliases,
 * transactions, projections and persisted story snapshots are all produced by real application services.
 */
@SpringBootTest(properties = "openai.api-key=")
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresTestContainerInitializer.class)
class MoneyStoriesIT {
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
    @MockBean private ExpenseNormalizationPort normalizer;
    @MockBean private ExpenseConfirmationPort confirmationPort;

    @Test
    void materializesStoriesFromDailyAggregatesAcrossFourDays() {
        String externalUserId = "money-story-it-user";
        Fixture fixture = fixture();
        YearMonth month = YearMonth.parse(fixture.month());
        LocalDate startDate = LocalDate.parse(fixture.startDate());
        for (DayFixture day : fixture.days()) {
            LocalDate date = startDate.plusDays(day.offset());
            for (TransactionFixture transaction : day.transactions()) {
                assertThat(taxonomy.spendingNatureFor(transaction.category(), transaction.subcategory()))
                        .as("fixture taxonomy: %s / %s", transaction.category(), transaction.subcategory())
                        .isPresent();
                confirmExpense(externalUserId, fixture.sourceAccount(), date, transaction);
            }
            runDailyAndStoryJobs(date);
            assertEarlyObservationsAreCompactAndGrounded(user(externalUserId), month);
        }
        AppUserEntity user = user(externalUserId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM money_story_snapshot WHERE user_id=?", Integer.class,
                user.getId())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_reference_entity WHERE user_id=?", Integer.class,
                user.getId())).isGreaterThan(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_reference_alias alias JOIN user_reference_entity ref ON ref.id=alias.reference_entity_id WHERE ref.user_id=?", Integer.class,
                user.getId())).isGreaterThan(1);
    }

    private Fixture fixture() {
        try (var input = new ClassPathResource("money-stories/four-day-story-fixture.json").getInputStream()) {
            return new ObjectMapper().findAndRegisterModules().readValue(input, Fixture.class);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load Money Stories integration fixture", failure);
        }
    }

    private void runDailyAndStoryJobs(LocalDate date) {
        dailyAggregation.rebuild(date);
        storyCron.generateMissingAndStaleSnapshots();
    }

    private void assertEarlyObservationsAreCompactAndGrounded(AppUserEntity user, YearMonth month) {
        assertThat(stories.monthly(user, month).stories())
                .as("Four-day capture produces compact observations rather than historical claims")
                .isNotEmpty().hasSizeLessThanOrEqualTo(3)
                .allSatisfy(story -> {
                    assertThat(story.level()).isEqualTo(com.apps.deen_sa.domain.MoneyStoryLevel.OBSERVATION);
                    assertThat(story.cards()).hasSize(1);
                    assertThat(story.observation()).isNotNull();
                    assertThat(story.observation().focusAmount().add(story.observation().otherAmount()))
                            .isEqualByComparingTo(story.observation().total());
                });
    }

    private AppUserEntity user(String externalId) {
        return users.findByChannelAndExternalUserId("WHATSAPP", externalId).orElseThrow();
    }

    private void confirmExpense(String externalUserId, String sourceAccount, LocalDate date, TransactionFixture fixture) {
        String messageId = "money-story-it-" + java.util.UUID.randomUUID();
        String rawText = fixture.merchant() + " " + fixture.amount();
        InboundMessage inbound = new InboundMessage(externalUserId, messageId, InputType.TEXT, MessageSource.WHATSAPP, rawText);
        when(normalizer.normalize(eq(externalUserId), eq(rawText), any(LocalDate.class))).thenReturn(
                new ExpenseNormalizationPort.ExpenseFacts(new BigDecimal(fixture.amount()), fixture.category(),
                        fixture.subcategory(), fixture.merchant(), sourceAccount, date, new BigDecimal("0.99")));
        var draft = drafts.routeAndCommit(inbound);
        normalization.handle(draft, inbound);
        long extractionId = extractions.findByDraftIdAndStatus(draft.draftId(),
                com.apps.deen_sa.domain.TransactionDraftExtractionStatus.ACTIVE).orElseThrow().getId();
        confirmation.handle(new ExpenseConfirmationCommand(externalUserId, extractionId,
                ExpenseConfirmationCommand.Action.CONFIRM));
    }

    private record Fixture(String month, String startDate, String sourceAccount, List<DayFixture> days) { }
    private record DayFixture(int offset, List<TransactionFixture> transactions) { }
    private record TransactionFixture(String spendingNature, String category, String subcategory,
                                      String merchant, String amount) { }
}
