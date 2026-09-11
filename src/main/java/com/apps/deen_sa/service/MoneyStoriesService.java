package com.apps.deen_sa.service;

import com.apps.deen_sa.config.MoneyStoriesProperties;
import com.apps.deen_sa.domain.MoneyStoryLevel;
import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import static com.apps.deen_sa.service.MoneyStoryRenderer.money;

/** Coordinates evaluation and atomic snapshot publication. Web reads never generate stories. */
@Service
@RequiredArgsConstructor
public class MoneyStoriesService {
    private final MoneyStorySnapshotRepository snapshots;
    private final MoneyStoryRepository stories;
    private final MoneyStoryEvidenceRepository evidence;
    private final MoneyStoryAggregateRepository aggregates;
    private final FinancialTransactionRepository transactions;
    private final List<MoneyStoryRule> rules;
    private final MoneyStoriesProperties properties;
    private final MoneyStorySelector selector;
    private final MoneyStoryRenderer renderer;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional(readOnly = true)
    public MoneyStoriesResponse monthly(AppUserEntity user, YearMonth month) {
        return snapshots.findByUserIdAndScopeMonthAndSupersededAtIsNull(user.getId(), month.atDay(1))
                .map(this::response).orElseGet(MoneyStoriesResponse::empty);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void generateIfNeeded(AppUserEntity user, YearMonth month) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
        if (month.atDay(1).isAfter(today)) return;
        var previous = snapshots.findByUserIdAndScopeMonthAndSupersededAtIsNull(user.getId(), month.atDay(1)).orElse(null);
        StoryEvaluationContext context = loadContext(user, month, today);
        var candidates = rules.stream().filter(MoneyStoryRule::enabled)
                .map(rule -> rule.evaluate(context)).flatMap(Optional::stream).toList();
        List<PreparedStory> prepared = prepareEvidence(user, selector.select(candidates));
        String fingerprint = hash(user.getCurrency() + ":" + user.getLocale() + ":" + user.getTimezone() + ":v2:" +
                prepared.stream().map(PreparedStory::fingerprint).toList());
        Instant watermark = Stream.concat(context.history().stream().map(MoneyStoryAggregateRepository.CategoryDay::updatedAt),
                        context.references().stream().map(MoneyStoryAggregateRepository.ReferenceDay::updatedAt))
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(Instant.EPOCH);

        if (previous != null && fingerprint.equals(previous.getContentFingerprint())) {
            // A rebuild or a calendar tick with unchanged facts is not a new discovery.
            previous.setStatus("READY");
            previous.setEvaluatedOn(today);
            previous.setInputWatermark(watermark);
            return;
        }
        publish(user, month, today, previous, prepared, fingerprint, watermark);
    }

    @Transactional
    public void invalidate(AppUserEntity user, LocalDate changedDate) {
        // Includes any future month containing a day whose trailing baseline uses this expense.
        LocalDate lastAffected = changedDate.plusWeeks(Math.max(4, properties.rules().unusualHighSpendDay().baselineWeeks()));
        YearMonth end = YearMonth.from(lastAffected);
        YearMonth nextMonth = YearMonth.from(changedDate).plusMonths(1);
        if (nextMonth.isAfter(end)) end = nextMonth;
        for (YearMonth month = YearMonth.from(changedDate); !month.isAfter(end); month = month.plusMonths(1)) {
            snapshots.findByUserIdAndScopeMonthAndSupersededAtIsNull(user.getId(), month.atDay(1))
                    .ifPresent(snapshot -> snapshot.setStatus("STALE"));
        }
    }

    private StoryEvaluationContext loadContext(AppUserEntity user, YearMonth month, LocalDate today) {
        LocalDate start = month.atDay(1);
        LocalDate end = today.isBefore(month.atEndOfMonth()) ? today.plusDays(1) : month.plusMonths(1).atDay(1);
        int weeks = Math.max(8, properties.rules().unusualHighSpendDay().baselineWeeks());
        return new StoryEvaluationContext(month, today,
                aggregates.categories(user.getId(), start, end),
                aggregates.categories(user.getId(), start.minusMonths(1), start),
                aggregates.categories(user.getId(), start.minusWeeks(weeks), start),
                aggregates.references(user.getId(), start.minusWeeks(weeks), end));
    }

    private List<PreparedStory> prepareEvidence(AppUserEntity user, List<MoneyStoryCandidate> candidates) {
        List<PreparedStory> result = new ArrayList<>();
        Set<List<Long>> includedEvidence = new HashSet<>();
        for (var candidate : candidates) {
            var rows = transactions.findStoryEvidence(user.getId(), candidate.evidenceStart(), candidate.evidenceEnd(),
                    candidate.merchantId() == null ? candidate.category() : null, candidate.nature(), candidate.merchantId())
                    .stream().filter(tx -> candidate.type() != MoneyStoryType.WEEKEND_SPENDING_PATTERN
                            || MoneyStoryRuleSupport.weekend(tx.getOccurredAt())).toList();
            BigDecimal total = rows.stream().map(FinancialTransactionEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal expected = candidate.type() == MoneyStoryType.CATEGORY_SPENDING_GROWTH && candidate.level() == MoneyStoryLevel.PATTERN
                    ? candidate.impact().add(candidate.comparison()) : candidate.impact();
            if (total.compareTo(expected) != 0 || rows.size() != candidate.count()) {
                throw new IllegalStateException("Story aggregates need rebuilding before evidence can be published");
            }
            // Exact duplicate evidence adds no useful second observation to the same deck.
            if (!includedEvidence.add(rows.stream().map(FinancialTransactionEntity::getId).toList())) continue;
            String content = candidate + ":" + rows.stream().map(tx -> tx.getId() + ":" + tx.getAmount()
                    + ":" + tx.getOccurredAt() + ":" + tx.getCategory() + ":"
                    + (tx.getMerchant() == null ? "" : tx.getMerchant().getCanonicalName())).toList();
            result.add(new PreparedStory(candidate, rows, hash(content)));
        }
        return result;
    }

    private void publish(AppUserEntity user, YearMonth month, LocalDate today, MoneyStorySnapshotEntity previous,
            List<PreparedStory> prepared, String fingerprint, Instant watermark) {
        Map<String, MoneyStoryEntity> priorStories = new HashMap<>();
        if (previous != null) {
            stories.findBySnapshotIdOrderByImpactAmountDesc(previous.getId()).forEach(s -> priorStories.put(s.getStoryKey(), s));
            previous.setSupersededAt(clock.instant());
            snapshots.saveAndFlush(previous); // Release the one-current-snapshot constraint before insertion.
        }
        var snapshot = new MoneyStorySnapshotEntity();
        snapshot.setId(UUID.randomUUID()); snapshot.setUser(user); snapshot.setScopeMonth(month.atDay(1));
        snapshot.setTimezone(user.getTimezone()); snapshot.setLocale(user.getLocale()); snapshot.setCurrency(user.getCurrency());
        snapshot.setStatus("READY"); snapshot.setGeneratedAt(clock.instant()); snapshot.setCalculationVersion(2);
        snapshot.setInputWatermark(watermark); snapshot.setEvaluatedOn(today); snapshot.setContentFingerprint(fingerprint);
        snapshots.saveAndFlush(snapshot);
        for (PreparedStory item : prepared) {
            var c = item.candidate();
            var old = priorStories.get(c.logicalKey());
            StoryDto oldDto = old == null ? null : parse(old);
            String logicalId = UUID.nameUUIDFromBytes((user.getId() + ":" + month + ":" + c.logicalKey())
                    .getBytes(StandardCharsets.UTF_8)).toString();
            int revision = oldDto == null ? 1 : Math.max(1, oldDto.revision()) + (item.fingerprint().equals(old.getContentHash()) ? 0 : 1);
            StoryDto dto = old != null && item.fingerprint().equals(old.getContentHash())
                    ? new StoryDto(UUID.randomUUID().toString(), oldDto.storyType(), oldDto.templateVersion(), oldDto.generatedAt(),
                        oldDto.period(), oldDto.cardFace(), oldDto.cards(), null, oldDto.level(), logicalId, revision, oldDto.updatedReason())
                    : renderer.render(c, user, logicalId, revision, snapshot.getGeneratedAt());
            var entity = new MoneyStoryEntity();
            entity.setId(UUID.fromString(dto.storyId())); entity.setSnapshot(snapshot); entity.setStoryKey(c.logicalKey());
            entity.setStoryType(c.type().name()); entity.setStoryLevel(c.level()); entity.setRuleVersion(2); entity.setTemplateVersion(2);
            entity.setPeriodType(dto.period().type()); entity.setPeriodStart(c.periodStart()); entity.setPeriodEnd(c.periodEnd());
            entity.setImpactAmount(c.impact()); entity.setContentHash(item.fingerprint());
            try { entity.setPayload(mapper.writeValueAsString(dto)); }
            catch (Exception failure) { throw new IllegalStateException("Could not serialize story", failure); }
            stories.save(entity);
            saveEvidence(entity, item.transactions());
        }
    }

    private void saveEvidence(MoneyStoryEntity story, List<FinancialTransactionEntity> rows) {
        short ordinal = 0;
        for (var tx : rows) {
            var row = new MoneyStoryEvidenceEntity();
            var id = new MoneyStoryEvidenceEntity.MoneyStoryEvidenceId();
            id.setStoryId(story.getId()); id.setOrdinal(ordinal++); row.setId(id); row.setStory(story);
            row.setTransaction(tx); row.setAmount(tx.getAmount()); row.setOccurredAt(tx.getOccurredAt());
            row.setMerchantLabel(tx.getMerchant() == null ? null : tx.getMerchant().getCanonicalName());
            row.setCategoryLabel(tx.getCategory()); evidence.save(row);
        }
    }

    private StoryDto parse(MoneyStoryEntity entity) {
        try { return mapper.readValue(entity.getPayload(), StoryDto.class); }
        catch (Exception failure) { throw new IllegalStateException("Stored story is invalid", failure); }
    }

    private MoneyStoriesResponse response(MoneyStorySnapshotEntity snapshot) {
        List<StoryDto> result = stories.findBySnapshotIdOrderByImpactAmountDesc(snapshot.getId()).stream().map(s -> {
            StoryDto parsed = parse(s);
            var rows = evidence.findByStoryIdOrderByIdOrdinal(s.getId());
            BigDecimal total = rows.stream().map(MoneyStoryEvidenceEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            var value = new EvidenceDto("Included in this story", rows.size(),
                    new Component("MONEY", "Total", total, snapshot.getCurrency(), money(total, snapshot.getCurrency())),
                    rows.stream().map(row -> new EvidenceTransaction(Long.toString(row.getTransaction().getId()),
                            row.getOccurredAt().format(DateTimeFormatter.ofPattern("d MMM")), row.getMerchantLabel(),
                            row.getCategoryLabel(), new Component("MONEY", "Amount", row.getAmount(), snapshot.getCurrency(), money(row.getAmount(), snapshot.getCurrency())))).toList());
            return new StoryDto(parsed.storyId(), parsed.storyType(), parsed.templateVersion(), parsed.generatedAt(), parsed.period(),
                    parsed.cardFace(), parsed.cards(), value, parsed.level(), parsed.logicalStoryId(), parsed.revision(), parsed.updatedReason());
        }).sorted(Comparator.comparingInt((StoryDto s) -> s.level() == MoneyStoryLevel.PATTERN ? 0 : 1)
                .thenComparing(s -> s.period().startDate(), Comparator.reverseOrder()).thenComparing(StoryDto::storyType)).toList();
        return new MoneyStoriesResponse(snapshot.getGeneratedAt(), !"READY".equals(snapshot.getStatus()), result);
    }

    private static String hash(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private record PreparedStory(MoneyStoryCandidate candidate, List<FinancialTransactionEntity> transactions, String fingerprint) { }
    public record MoneyStoriesResponse(Instant generatedAt, boolean stale, List<StoryDto> stories) {
        public static MoneyStoriesResponse empty() { return new MoneyStoriesResponse(null, false, List.of()); }
    }
    public record StoryDto(String storyId, String storyType, int templateVersion, Instant generatedAt, PeriodDto period,
            CardFace cardFace, List<CardDto> cards, Object evidence, MoneyStoryLevel level, String logicalStoryId, int revision, String updatedReason) { }
    public record PeriodDto(String type, LocalDate startDate, LocalDate endDate, String displayLabel) { }
    public record CardFace(String heading, String displayValue, String theme) { }
    public record CardDto(String cardId, int sequence, String layout, String theme, String eyebrow, String title, String body, List<Component> components, List<Action> actions) { }
    public record Component(String type, String label, BigDecimal value, String currency, String displayValue) { }
    public record Action(String type, String label) { }
    public record EvidenceDto(String title, int totalCount, Component totalAmount, List<EvidenceTransaction> transactions) { }
    public record EvidenceTransaction(String transactionId, String dateLabel, String merchantLabel, String categoryLabel, Component amount) { }
}
