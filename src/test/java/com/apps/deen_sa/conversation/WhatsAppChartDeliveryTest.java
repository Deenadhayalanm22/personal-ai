package com.apps.deen_sa.conversation;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;

class WhatsAppChartDeliveryTest {
    @Test
    void sendsPortalButtonForInsightResultInsteadOfChart() {
        ConversationChannelGateway conversation = mock(ConversationChannelGateway.class);
        WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
        InboundMessageService inbound = mock(InboundMessageService.class);
        UserFeatureFlagService flags = mock(UserFeatureFlagService.class);
        MagicLinkService links = mock(MagicLinkService.class);
        SpeechResult result = SpeechResult.builder().status(SpeechStatus.INFO)
                .message("Total spent: ₹12,000 this month.\n\nFor detailed breakdowns and more insights, open the portal.")
                .actions(List.of(new ResponseAction("portal:insights", "Open portal"))).build();
        when(inbound.claim("WHATSAPP", "m0", "9199")).thenReturn(3L);
        when(flags.hasAnyEnabledFeature("WHATSAPP", "9199")).thenReturn(true);
        when(conversation.process("WHATSAPP", "9199", "m0", "show spending")).thenReturn(result);
        when(links.portalUrl()).thenReturn("https://money.example.com/portal");
        WhatsAppMessageProcessor processor = new WhatsAppMessageProcessor(conversation, inbound, replies,
                mock(WhatsAppMediaDownloader.class), mock(AudioTranscriber.class),
                mock(AudioConfirmationService.class), flags, mock(WhatsAppAccessCommandService.class), links);

        processor.processIncomingMessage("9199", "show spending", "m0");

        verify(replies).sendPortalLink("9199", result.getMessage(), "https://money.example.com/portal");
        verify(inbound).complete(3L);
    }

    @Test
    void sendsChartWithTextAsCaption() {
        ConversationChannelGateway conversation = mock(ConversationChannelGateway.class);
        WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
        InboundMessageService inbound = mock(InboundMessageService.class);
        UserFeatureFlagService flags = mock(UserFeatureFlagService.class);
        ResponseMedia chart = new ResponseMedia(new byte[]{1, 2, 3}, "image/png", "chart.png");
        SpeechResult result = SpeechResult.builder().status(SpeechStatus.INFO).message("You spent ₹12,000.")
                .media(chart).build();
        when(inbound.claim("WHATSAPP", "m1", "9199")).thenReturn(1L);
        when(flags.hasAnyEnabledFeature("WHATSAPP", "9199")).thenReturn(true);
        when(conversation.process("WHATSAPP", "9199", "m1", "show spending")).thenReturn(result);
        when(replies.sendImageReply("9199", chart, "You spent ₹12,000.")).thenReturn(true);
        WhatsAppMessageProcessor processor = new WhatsAppMessageProcessor(conversation, inbound, replies,
                mock(WhatsAppMediaDownloader.class), mock(AudioTranscriber.class),
                mock(AudioConfirmationService.class), flags, mock(WhatsAppAccessCommandService.class),
                mock(MagicLinkService.class));

        processor.processIncomingMessage("9199", "show spending", "m1");

        verify(replies).sendImageReply("9199", chart, "You spent ₹12,000.");
        verify(inbound).complete(1L);
    }

    @Test
    void fallsBackToTextWhenChartDeliveryFails() {
        ConversationChannelGateway conversation = mock(ConversationChannelGateway.class);
        WhatsAppReplySender replies = mock(WhatsAppReplySender.class);
        InboundMessageService inbound = mock(InboundMessageService.class);
        UserFeatureFlagService flags = mock(UserFeatureFlagService.class);
        ResponseMedia chart = new ResponseMedia(new byte[]{1}, "image/png", "chart.png");
        SpeechResult result = SpeechResult.builder().status(SpeechStatus.INFO).message("Summary").media(chart).build();
        when(inbound.claim("WHATSAPP", "m2", "9199")).thenReturn(2L);
        when(flags.hasAnyEnabledFeature("WHATSAPP", "9199")).thenReturn(true);
        when(conversation.process("WHATSAPP", "9199", "m2", "chart")).thenReturn(result);
        when(replies.sendImageReply("9199", chart, "Summary")).thenReturn(false);
        WhatsAppMessageProcessor processor = new WhatsAppMessageProcessor(conversation, inbound, replies,
                mock(WhatsAppMediaDownloader.class), mock(AudioTranscriber.class),
                mock(AudioConfirmationService.class), flags, mock(WhatsAppAccessCommandService.class),
                mock(MagicLinkService.class));

        processor.processIncomingMessage("9199", "chart", "m2");

        verify(replies).sendTextReply("9199", "Summary");
    }
}
