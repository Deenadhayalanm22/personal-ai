package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.v2.dto.InboundMessage;
import com.apps.deen_sa.v2.domain.InputType;
import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppInboundMessageMapperTest {
    private final WhatsAppInboundMessageMapper mapper = new WhatsAppInboundMessageMapper();

    @Test
    void mapsTextAndAudioIntoTheSameDomainContract() {
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload(List.of(
                new WhatsAppWebhookPayload.Entry(List.of(
                        new WhatsAppWebhookPayload.Change(new WhatsAppWebhookPayload.Value(List.of(
                                new WhatsAppWebhookPayload.Message(
                                        "wamid.text", "9198", "text",
                                        new WhatsAppWebhookPayload.Text("Paid ₹250"), null),
                                new WhatsAppWebhookPayload.Message(
                                        "wamid.audio", "9198", "audio", null,
                                        new WhatsAppWebhookPayload.Audio("media-1", "audio/ogg"))
                        )))
                ))
        ));

        List<InboundMessage> messages = mapper.map(payload);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).inputType()).isEqualTo(InputType.TEXT);
        assertThat(messages.get(0).rawContent()).isEqualTo("Paid ₹250");
        assertThat(messages.get(1).inputType()).isEqualTo(InputType.AUDIO);
        assertThat(messages.get(1).rawContent())
                .isEqualTo("media_id=media-1;mime_type=audio/ogg");
    }
}
