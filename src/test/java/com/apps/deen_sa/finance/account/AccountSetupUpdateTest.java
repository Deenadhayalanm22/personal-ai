package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.llm.impl.AccountSetupClassifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountSetupUpdateTest {
    @Test
    void explicitLaterDetailUpdatesExistingAccountWithoutCreatingDuplicate() {
        AccountSetupClassifier classifier = mock(AccountSetupClassifier.class);
        StateContainerRepository repository = mock(StateContainerRepository.class);
        StateContainerService containers = mock(StateContainerService.class);
        AccountSetupHandler handler = new AccountSetupHandler(classifier, repository, containers);
        ConversationContext context = new ConversationContext();
        context.setUserId(7L);

        StateContainerEntity existing = new StateContainerEntity();
        existing.setId(12L);
        existing.setOwnerId(7L);
        existing.setOwnerType("USER");
        existing.setContainerType("CREDIT_CARD");
        existing.setName("HDFC credit card");
        existing.setStatus("ACTIVE");
        existing.setCurrency("INR");
        existing.setCurrentValue(BigDecimal.ZERO);
        existing.setCapacityLimit(new BigDecimal("50000"));
        existing.setDetails(new HashMap<>(Map.of("dueDay", 21)));

        AccountSetupDto update = new AccountSetupDto();
        update.setValid(true);
        update.setContainerType("CREDIT_CARD");
        update.setName("HDFC credit card");
        update.setDetails(Map.of("billingDay", 1));
        when(classifier.extractAccount(any())).thenReturn(update);
        when(repository.findActiveByOwnerId(7L)).thenReturn(List.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        SpeechResult result = handler.handleSpeech("My HDFC credit card bill is generated on the 1st", context);

        assertThat(result.getStatus()).isEqualTo(SpeechStatus.SAVED);
        assertThat(result.getMessage()).contains("Updated HDFC credit card");
        assertThat(existing.getDetails()).containsEntry("billingDay", 1).containsEntry("dueDay", 21);
        verify(repository).save(existing);
        verify(containers).evictCache(7L);
    }
}
