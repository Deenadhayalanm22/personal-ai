package com.apps.deen_sa.conversation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMessagesTest {
    private final ConversationMessages messages = new ConversationMessages();

    @Test
    void rendersTamilCoreHelpWithoutBusinessVocabulary() {
        assertThat(messages.gettingStarted("ta-IN")).contains("செயல்பாடுகளை");
    }

    @Test
    void keepsRomanizedTamilOnEnglishFallbackUntilUserChoosesTamilScript() {
        assertThat(messages.gettingStarted("ta-Latn")).contains("operational activity");
    }
}
