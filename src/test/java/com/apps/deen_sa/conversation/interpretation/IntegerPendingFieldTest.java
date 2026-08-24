package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ConversationMessages;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.UnprocessedConversationService;
import com.apps.deen_sa.extension.api.CapabilityResult;
import com.apps.deen_sa.extension.api.EventCapability;
import com.apps.deen_sa.extension.api.ExtensionEvent;
import com.apps.deen_sa.extension.runtime.ExtensionCatalog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegerPendingFieldTest {
    @Test
    void routesBareBillingDayDirectlyWithoutCallingInterpreter() {
        ConversationInterpreter interpreter = mock(ConversationInterpreter.class);
        ExtensionCatalog catalog = mock(ExtensionCatalog.class);
        EventCapability capability = mock(EventCapability.class);
        when(catalog.event(7L, "EXPENSE")).thenReturn(Optional.of(capability));
        when(capability.fieldTypes()).thenReturn(Map.of("creditCardBillingDay", "integer"));
        when(capability.handle(any(), any(), any(), any(Boolean.class))).thenReturn(
                new CapabilityResult("FOLLOWUP", "Continue expense confirmation", true,
                        List.of("confirmExpense"), null, null, List.of(), null));

        UnifiedConversationEngine engine = new UnifiedConversationEngine(interpreter, catalog,
                mock(MutationAuthorizationPolicy.class), mock(ConversationMessages.class),
                mock(UnprocessedConversationService.class));
        ConversationContext context = new ConversationContext();
        context.setUserId(7L);
        context.setActiveIntent("EXPENSE");
        context.setWaitingForField("creditCardBillingDay");
        context.setPartialObject(Map.of("amount", 622));

        var result = engine.process("1", context);

        assertThat(result.getStatus()).isEqualTo(SpeechStatus.FOLLOWUP);
        ArgumentCaptor<ExtensionEvent> event = ArgumentCaptor.forClass(ExtensionEvent.class);
        verify(capability).handle(event.capture(), org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.eq(context), org.mockito.ArgumentMatchers.eq(true));
        assertThat(((EventPatch) event.getValue()).fields().asMap().get("creditCardBillingDay"))
                .isEqualTo(new BigDecimal("1"));
        verify(interpreter, never()).interpret(any(), any());
    }
}
