package com.apps.deen_sa.finance.account.enrichment;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountEnrichmentServiceTest {
    private final AccountEnrichmentPreferenceRepository repository = mock(AccountEnrichmentPreferenceRepository.class);
    private final Map<String, AccountEnrichmentPreferenceEntity> stored = new HashMap<>();
    private final AccountEnrichmentService service = new AccountEnrichmentService(repository);

    @BeforeEach
    void setUp() {
        stored.clear();
        when(repository.findByAccountIdAndFieldName(any(), any())).thenAnswer(call ->
                Optional.ofNullable(stored.get(call.getArgument(0) + ":" + call.getArgument(1))));
        when(repository.save(any())).thenAnswer(call -> {
            AccountEnrichmentPreferenceEntity value = call.getArgument(0);
            stored.put(value.getAccountId() + ":" + value.getFieldName(), value);
            return value;
        });
    }

    @Test
    void findsCreditCardFieldsInStableCollectionOrder() {
        StateContainerEntity card = card();

        assertThat(service.missingFields(card)).containsExactly(
                "sourceBalance", "creditLimit", "creditCardBillingDay", "creditCardDueDay");

        card.setCurrentValue(BigDecimal.ZERO);
        card.setCapacityLimit(new BigDecimal("50000"));
        card.setDetails(Map.of("dueDay", 21));
        assertThat(service.nextPromptableField(card)).contains("creditCardBillingDay");
    }

    @Test
    void snoozeAndDisableControlFutureAutomaticPrompts() {
        StateContainerEntity card = card();
        card.setCurrentValue(BigDecimal.ZERO);
        card.setCapacityLimit(new BigDecimal("50000"));
        card.setDetails(Map.of("dueDay", 21));

        service.snooze(card, "creditCardBillingDay");
        assertThat(service.nextPromptableField(card)).isEmpty();
        assertThat(stored.get("12:creditCardBillingDay").getPromptStatus()).isEqualTo("SNOOZED");

        service.disableAutomaticPrompts(card, "creditCardBillingDay");
        assertThat(service.nextPromptableField(card)).isEmpty();
        assertThat(stored.get("12:creditCardBillingDay").getPromptStatus()).isEqualTo("AUTO_PROMPT_DISABLED");
    }

    @Test
    void completedFieldBecomesPendingAgainIfItsValueIsLaterRemoved() {
        StateContainerEntity card = card();
        card.setCurrentValue(BigDecimal.ZERO);
        card.setCapacityLimit(new BigDecimal("50000"));
        card.setDetails(new HashMap<>(Map.of("billingDay", 1, "dueDay", 21)));
        service.completed(card, "creditCardBillingDay");

        card.getDetails().remove("billingDay");

        assertThat(service.nextPromptableField(card)).contains("creditCardBillingDay");
    }

    private StateContainerEntity card() {
        StateContainerEntity card = new StateContainerEntity();
        card.setId(12L);
        card.setOwnerId(7L);
        card.setContainerType("CREDIT_CARD");
        return card;
    }
}
