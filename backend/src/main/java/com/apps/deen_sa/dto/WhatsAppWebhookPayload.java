package com.apps.deen_sa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public record WhatsAppWebhookPayload(List<Entry> entry) {

    public List<UserMessage> extractUserMessages() {
        return messages().stream()
                .filter(m -> "text".equals(m.type()) && m.text() != null)
                .map(m -> new UserMessage(m.from(), m.text().body(), m.id()))
                .toList();
    }

    public List<AudioMessage> extractAudioMessages() {
        return messages().stream()
                .filter(m -> "audio".equals(m.type()) && m.audio() != null)
                .map(m -> new AudioMessage(m.from(), m.audio().id(), m.audio().mimeType(), m.id()))
                .toList();
    }

    public List<InteractiveMessage> extractInteractiveMessages() {
        return messages().stream()
                .filter(m -> "interactive".equals(m.type())
                        && m.interactive() != null
                        && (m.interactive().buttonReply() != null || m.interactive().listReply() != null))
                .map(m -> new InteractiveMessage(
                        m.from(), m.interactive().replyId(), m.id()))
                .toList();
    }

    public List<Message> messages() {
        if (entry == null) {
            return List.of();
        }
        return entry.stream()
                .filter(Objects::nonNull)
                .filter(e -> e.changes() != null)
                .flatMap(e -> e.changes().stream())
                .filter(Objects::nonNull)
                .filter(change -> change.value() != null && change.value().messages() != null)
                .flatMap(change -> change.value().messages().stream())
                .filter(Objects::nonNull)
                .toList();
    }

    public record Entry(List<Change> changes) {}
    public record Change(Value value) {}
    public record Value(List<Message> messages) {}
    public record Message(String id, String from, String type, Text text, Audio audio, Interactive interactive) {
        public Message(String id, String from, String type, Text text, Audio audio) {
            this(id, from, type, text, audio, null);
        }
    }
    public record Text(String body) {}
    public record Audio(String id, @JsonProperty("mime_type") String mimeType) {}
    public record Interactive(
            String type,
            @JsonProperty("button_reply") ButtonReply buttonReply,
            @JsonProperty("list_reply") ListReply listReply
    ) {
        public String replyId() {
            return buttonReply != null ? buttonReply.id() : listReply != null ? listReply.id() : null;
        }
    }
    public record ButtonReply(String id, String title) {}
    public record ListReply(String id, String title, String description) {}
    public record AudioMessage(String from, String mediaId, String mimeType, String messageId) {}
    public record InteractiveMessage(String from, String buttonId, String messageId) {}
}
