package com.apps.deen_sa.finance.account.enrichment;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountEnrichmentService {
    public static final String PENDING = "PENDING";
    public static final String SNOOZED = "SNOOZED";
    public static final String AUTO_PROMPT_DISABLED = "AUTO_PROMPT_DISABLED";
    public static final String COMPLETED = "COMPLETED";
    private static final Duration DEFAULT_SNOOZE = Duration.ofDays(7);
    private static final Map<String, List<String>> RULES = Map.of(
            "BANK_ACCOUNT", List.of("sourceBalance"),
            "CREDIT_CARD", List.of("sourceBalance", "creditLimit", "creditCardBillingDay", "creditCardDueDay"),
            "CASH", List.of("sourceBalance"),
            "WALLET", List.of("sourceBalance")
    );

    private final AccountEnrichmentPreferenceRepository repository;
    private final Clock clock = Clock.systemUTC();

    public Optional<String> nextPromptableField(StateContainerEntity account) {
        Instant now = clock.instant();
        return RULES.getOrDefault(account.getContainerType(), List.of()).stream()
                .filter(field -> isMissing(account, field))
                .filter(field -> repository.findByAccountIdAndFieldName(account.getId(), field)
                        .map(preference -> promptable(preference, now)).orElse(true))
                .findFirst();
    }

    public List<String> missingFields(StateContainerEntity account) {
        return RULES.getOrDefault(account.getContainerType(), List.of()).stream()
                .filter(field -> isMissing(account, field)).toList();
    }

    @Transactional
    public void prompted(StateContainerEntity account, String field) {
        AccountEnrichmentPreferenceEntity preference = preference(account, field);
        preference.setLastPromptedAt(clock.instant());
        preference.setPromptCount(preference.getPromptCount() + 1);
        preference.setPromptStatus(PENDING);
        preference.setRemindAfter(null);
        repository.save(preference);
    }

    @Transactional
    public void snooze(StateContainerEntity account, String field) {
        AccountEnrichmentPreferenceEntity preference = preference(account, field);
        preference.setPromptStatus(SNOOZED);
        preference.setRemindAfter(clock.instant().plus(DEFAULT_SNOOZE));
        repository.save(preference);
    }

    @Transactional
    public void disableAutomaticPrompts(StateContainerEntity account, String field) {
        AccountEnrichmentPreferenceEntity preference = preference(account, field);
        preference.setPromptStatus(AUTO_PROMPT_DISABLED);
        preference.setRemindAfter(null);
        repository.save(preference);
    }

    @Transactional
    public void completed(StateContainerEntity account, String field) {
        AccountEnrichmentPreferenceEntity preference = preference(account, field);
        preference.setPromptStatus(COMPLETED);
        preference.setRemindAfter(null);
        repository.save(preference);
    }

    public boolean isMissing(StateContainerEntity account, String field) {
        return switch (field) {
            case "sourceBalance" -> account.getCurrentValue() == null;
            case "creditLimit" -> account.getCapacityLimit() == null;
            case "creditCardBillingDay" -> detail(account, "billingDay") == null;
            case "creditCardDueDay" -> detail(account, "dueDay") == null;
            default -> false;
        };
    }

    private Object detail(StateContainerEntity account, String key) {
        return account.getDetails() == null ? null : account.getDetails().get(key);
    }

    private boolean promptable(AccountEnrichmentPreferenceEntity preference, Instant now) {
        return switch (preference.getPromptStatus()) {
            case AUTO_PROMPT_DISABLED -> false;
            case SNOOZED -> preference.getRemindAfter() == null || !preference.getRemindAfter().isAfter(now);
            default -> true;
        };
    }

    private AccountEnrichmentPreferenceEntity preference(StateContainerEntity account, String field) {
        return repository.findByAccountIdAndFieldName(account.getId(), field).orElseGet(() -> {
            AccountEnrichmentPreferenceEntity created = new AccountEnrichmentPreferenceEntity();
            created.setUserId(account.getOwnerId());
            created.setAccountId(account.getId());
            created.setFieldName(field);
            return created;
        });
    }
}
