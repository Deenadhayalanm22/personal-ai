package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.v2.dto.InboundMessage;
import com.apps.deen_sa.v2.domain.InputType;
import com.apps.deen_sa.v2.domain.MessageSource;
import com.apps.deen_sa.v2.dto.WhatsAppWebhookPayload;
import org.springframework.stereotype.Component;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Component
@Log4j2
public class WhatsAppInboundMessageMapper {
    public List<InboundMessage> map(WhatsAppWebhookPayload payload) {
        if (payload == null || payload.entry() == null) {
            return List.of();
        }

        List<InboundMessage> mapped = payload.entry().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.changes() != null)
                .flatMap(entry -> entry.changes().stream())
                .filter(Objects::nonNull)
                .filter(change -> change.value() != null && change.value().messages() != null)
                .flatMap(change -> change.value().messages().stream())
                .filter(Objects::nonNull)
                .flatMap(this::mapMessage)
                .toList();
        log.info("Mapped WhatsApp webhook payload: entries={}, supportedMessages={}",
                payload.entry().size(), mapped.size());
        return mapped;
    }

    private Stream<InboundMessage> mapMessage(WhatsAppWebhookPayload.Message message) {
        if ("text".equals(message.type()) && message.text() != null) {
            return Stream.of(new InboundMessage(
                    message.from(),
                    message.id(),
                    InputType.TEXT,
                    MessageSource.WHATSAPP,
                    message.text().body()));
        }

        if ("audio".equals(message.type()) && message.audio() != null) {
            String metadata = "media_id=" + message.audio().id()
                    + ";mime_type=" + message.audio().mimeType();
            return Stream.of(new InboundMessage(
                    message.from(),
                    message.id(),
                    InputType.AUDIO,
                    MessageSource.WHATSAPP,
                    metadata));
        }

        log.info("Ignoring unsupported WhatsApp message: id={}, type={}", message.id(), message.type());
        return Stream.empty();
    }
}
