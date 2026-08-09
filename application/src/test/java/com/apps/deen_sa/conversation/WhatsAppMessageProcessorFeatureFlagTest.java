package com.apps.deen_sa.conversation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppMessageProcessorFeatureFlagTest {
    private static final String MOBILE = "919876543210";

    private final ConversationChannelGateway conversation = mock(ConversationChannelGateway.class);
    private final InboundMessageService inboundMessages = mock(InboundMessageService.class);
    private final WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
    private final UserFeatureFlagService featureFlags = mock(UserFeatureFlagService.class);
    private WhatsAppMessageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WhatsAppMessageProcessor(
                conversation,
                inboundMessages,
                replies,
                mock(WhatsAppMediaDownloader.class),
                mock(AudioTranscriber.class),
                mock(AudioConfirmationService.class),
                featureFlags);
    }

    @Test
    void blocksExpenseProcessingForAMobileWithoutAccess() {
        when(inboundMessages.claim("WHATSAPP", "message-1", MOBILE)).thenReturn(42L);
        when(featureFlags.hasAnyEnabledFeature("WHATSAPP", MOBILE)).thenReturn(false);

        processor.processIncomingMessage(MOBILE, "Paid 500 for groceries", "message-1");

        verify(conversation, never()).process("WHATSAPP", MOBILE, "message-1", "Paid 500 for groceries");
        verify(replies).sendTextReply(MOBILE,
                "Access is not enabled for this mobile number. Please contact the administrator.");
        verify(inboundMessages).complete(42L);
    }

    @Test
    void processesMessagesForAnEnabledMobile() {
        when(inboundMessages.claim("WHATSAPP", "message-2", MOBILE)).thenReturn(43L);
        when(featureFlags.hasAnyEnabledFeature("WHATSAPP", MOBILE)).thenReturn(true);
        when(conversation.process("WHATSAPP", MOBILE, "message-2", "Paid 500 for groceries"))
                .thenReturn(SpeechResult.info("Expense saved."));

        processor.processIncomingMessage(MOBILE, "Paid 500 for groceries", "message-2");

        verify(conversation).process("WHATSAPP", MOBILE, "message-2", "Paid 500 for groceries");
        verify(replies).sendTextReply(MOBILE, "Expense saved.");
        verify(inboundMessages).complete(43L);
    }
}
