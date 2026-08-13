package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ConversationMessages;
import com.apps.deen_sa.conversation.UnprocessedConversationService;
import com.apps.deen_sa.extension.runtime.ExtensionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HelpRequestEvidenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"Hi", "help", "Could you help me?", "What can you do?", "உதவி"})
    void acceptsActualHelpAndGreetingEvidence(String text) {
        assertThat(UnifiedConversationEngine.isHelpRequest(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Purple silence sideways banana orbit", "spent 500", "show this month"})
    void rejectsUngroundedModelHelpClassification(String text) {
        assertThat(UnifiedConversationEngine.isHelpRequest(text)).isFalse();
    }

    @Test
    void rejectsModelInventedSkipWhenNoFollowupIsActive() {
        ConversationInterpreter interpreter = mock(ConversationInterpreter.class);
        ExtensionCatalog catalog = mock(ExtensionCatalog.class);
        UnprocessedConversationService unprocessed = mock(UnprocessedConversationService.class);
        ConversationContext context = new ConversationContext();
        context.setUserId(1L);
        when(catalog.queryDeterministically(1L, "Purple silence sideways banana orbit"))
                .thenReturn(Optional.empty());
        when(catalog.extractDeterministically(1L, "Purple silence sideways banana orbit"))
                .thenReturn(List.of());
        when(catalog.routeDeterministically(1L, "Purple silence sideways banana orbit"))
                .thenReturn(Optional.empty());
        when(catalog.context(1L, 1L)).thenReturn(Map.of());
        when(interpreter.interpret(anyString(), any())).thenReturn(new TurnInterpretation(
                TurnType.COMMAND, null, "en-IN", null, List.of(), "SKIP_PENDING",
                QueryPeriod.NONE, List.of(), 0.8));

        var engine = new UnifiedConversationEngine(interpreter, catalog,
                new MutationAuthorizationPolicy(), new ConversationMessages(), unprocessed);
        var result = engine.process("Purple silence sideways banana orbit", context);

        assertThat(result.getMessage()).containsIgnoringCase("couldn't understand");
        verify(unprocessed).record("Purple silence sideways banana orbit", "UNKNOWN_COMMAND", context);
    }
}
