package com.apps.deen_sa.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookPayloadTest {

    @Test
    void extractsAudioMessagesWithoutTreatingThemAsText() {
        WhatsAppWebhookPayload.Message message = new WhatsAppWebhookPayload.Message(
                "wamid.audio-1",
                "919876543210",
                "audio",
                null,
                new WhatsAppWebhookPayload.Audio("media-123", "audio/ogg; codecs=opus"),
                null
        );
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload(List.of(
                new WhatsAppWebhookPayload.Entry(List.of(
                        new WhatsAppWebhookPayload.Change(
                                new WhatsAppWebhookPayload.Value(List.of(message)))
                ))
        ));

        assertThat(payload.extractUserMessages()).isEmpty();
        assertThat(payload.extractAudioMessages()).containsExactly(
                new WhatsAppWebhookPayload.AudioMessage(
                        "919876543210", "media-123", "audio/ogg; codecs=opus", "wamid.audio-1")
        );
    }
}
