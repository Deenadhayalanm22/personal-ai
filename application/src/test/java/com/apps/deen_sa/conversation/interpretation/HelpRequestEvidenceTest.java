package com.apps.deen_sa.conversation.interpretation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

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
}
