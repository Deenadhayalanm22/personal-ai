package com.apps.deen_sa.integration;

import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.domain.MessageSource;
import com.apps.deen_sa.dto.ExpenseConfirmationCommand;
import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.entity.AppUserEntity;
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
import java.math.BigDecimal;
import java.time.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** Actions only: capture real expenses, apply corrections, advance time, and materialize stories. */
final class MoneyStoryScenarioDriver {
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final ExpenseDailyAggregationService dailyAggregation;
    private final MoneyStoryGenerationScheduler storyCron;
    private final MoneyStoriesService stories;
    private final TransactionDraftWriter drafts;
    private final ExpenseNormalizationHandler normalization;
    private final ExpenseConfirmationCommandHandler confirmation;
    private final TransactionDraftExtractionRepository extractions;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final FinancialTransactionEditService edits;
    private final ExpenseNormalizationPort normalizer;
    private final MoneyStoryCopyGenerator copy;
    private final Clock clock;

    MoneyStoryScenarioDriver(
            JdbcTemplate jdbc,
            AppUserRepository users,
            ExpenseDailyAggregationService dailyAggregation,
            MoneyStoryGenerationScheduler storyCron,
            MoneyStoriesService stories,
            TransactionDraftWriter drafts,
            ExpenseNormalizationHandler normalization,
            ExpenseConfirmationCommandHandler confirmation,
            TransactionDraftExtractionRepository extractions,
            ExpenseTaxonomyRegistry taxonomy,
            FinancialTransactionEditService edits,
            ExpenseNormalizationPort normalizer,
            MoneyStoryCopyGenerator copy,
            Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.dailyAggregation = dailyAggregation;
        this.storyCron = storyCron;
        this.stories = stories;
        this.drafts = drafts;
        this.normalization = normalization;
        this.confirmation = confirmation;
        this.extractions = extractions;
        this.taxonomy = taxonomy;
        this.edits = edits;
        this.normalizer = normalizer;
        this.copy = copy;
        this.clock = clock;
    }

    private final Map<String, Long> ids = new HashMap<>();
    private final Map<String, LocalDate> dates = new HashMap<>();
    private final Set<YearMonth> months = new TreeSet<>();
    private final Set<LocalDate> dirty = new HashSet<>();
    private String externalId;
    private String sourceAccount;

    void startUser(String persona, String account) {
        externalId = "ladder-" + persona + "-" + UUID.randomUUID();
        sourceAccount = account;
        when(copy.generate(any(), anyMap(), any())).thenAnswer(call -> call.getArgument(2));
    }

    void beginDay(LocalDate date) {
        dirty.clear();
        advanceClockTo(date, 10);
    }

    void recordTodaysExpenses(JsonNode day) {
        for (JsonNode entry : day.path("entries")) {
            LocalDate occurred = LocalDate.parse(entry.path("occurredOn").asText());
            String category = entry.path("category").asText(), subcategory = entry.path("subcategory").asText();
            assertThat(taxonomy.spendingNatureFor(category, subcategory)).isPresent();
            confirmExpense(externalId, sourceAccount, occurred,
                    new TransactionFixture(category, subcategory, entry.path("merchant").asText(null), entry.path("amount").asText()));
            long id = jdbc.queryForObject("SELECT max(id) FROM financial_transaction WHERE user_id=?", Long.class, user(externalId).getId());
            ids.put(entry.path("id").asText(), id);
            dates.put(entry.path("id").asText(), occurred);
            dirty.add(occurred);
            months.add(YearMonth.from(occurred));
        }
    }

    void applyTodaysCorrections(JsonNode day) {
        for (JsonNode change : day.path("edits")) {
            String key = change.path("transaction").asText();
            dirty.add(dates.get(key));
            switch (change.path("operation").asText()) {
                case "AMOUNT" -> edits.edit(user(externalId), ids.get(key), new FinancialTransactionEditService.ExpenseUpdate(
                        new BigDecimal(change.path("amount").asText()), null, null, null, null, null));
                case "DELETE" -> edits.delete(user(externalId), ids.get(key));
                case "MOVE" -> {
                    LocalDate moved = LocalDate.parse(change.path("occurredOn").asText());
                    edits.edit(user(externalId), ids.get(key), new FinancialTransactionEditService.ExpenseUpdate(null, moved, null, null, null, null));
                    dates.put(key, moved);
                    dirty.add(moved);
                    months.add(YearMonth.from(moved));
                }
                default -> throw new IllegalArgumentException("Unknown edit");
            }
        }
    }

    void runEndOfDayJobs(LocalDate date) {
        advanceClockTo(date, 23);
        for (LocalDate affected : dirty) dailyAggregation.rebuild(affected);
        rerunStoryJob(); // Run even on days with no entries.
    }

    void rerunStoryJob() { storyCron.generateMissingAndStaleSnapshots(); }
    boolean hasRecordedExpenses() { return !ids.isEmpty(); }
    Set<YearMonth> recordedMonths() { return new TreeSet<>(months); }
    long transactionId(String fixtureId) { return ids.get(fixtureId); }
    Collection<Long> recordedTransactionIds() { return List.copyOf(ids.values()); }
    AppUserEntity owner() { return user(externalId); }
    Instant evaluationTime() { return clock.instant(); }
    boolean expectsObservationIn(YearMonth month, LocalDate date) {
        return !dirty.isEmpty() && month.equals(YearMonth.from(date));
    }
    MoneyStoriesService.MoneyStoriesResponse readStories(YearMonth month) { return stories.monthly(owner(), month); }

    void advanceClockTo(LocalDate date, int hour) {
        Instant instant = date.atTime(hour, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        when(clock.instant()).thenReturn(instant);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(any(ZoneId.class))).thenAnswer(call -> Clock.fixed(instant, call.getArgument(0)));
    }
    AppUserEntity user(String externalId) {
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

    private record TransactionFixture(String category, String subcategory, String merchant, String amount) { }
}
