package com.apps.deen_sa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Stream;

public record WhatsAppWebhookPayload(List<Entry> entry) {

    public List<UserMessage> extractUserMessages() {

        if (entry == null) return List.of();

        return entry.stream()
                .flatMap(e -> e.changes().stream())
                .flatMap(c -> c.value().messages() != null
                        ? c.value().messages().stream()
                        : Stream.empty()
                )
                .filter(m -> "text".equals(m.type()) && m.text() != null)
                .map(m -> new UserMessage(m.from(), m.text().body()))
                .toList();
    }

    public List<AudioMessage> extractAudioMessages() {
        if (entry == null) return List.of();

        return entry.stream()
                .flatMap(e -> e.changes().stream())
                .flatMap(c -> c.value().messages() != null
                        ? c.value().messages().stream()
                        : Stream.empty())
                .filter(m -> "audio".equals(m.type()) && m.audio() != null)
                .map(m -> new AudioMessage(m.from(), m.audio().id(), m.audio().mimeType()))
                .toList();
    }

    public List<InteractiveMessage> extractInteractiveMessages() {
        if (entry == null) return List.of();

        return entry.stream()
                .flatMap(e -> e.changes().stream())
                .flatMap(c -> c.value().messages() != null
                        ? c.value().messages().stream()
                        : Stream.empty())
                .filter(m -> "interactive".equals(m.type())
                        && m.interactive() != null
                        && m.interactive().buttonReply() != null)
                .map(m -> new InteractiveMessage(
                        m.from(), m.interactive().buttonReply().id()))
                .toList();
    }

    public record Entry(List<Change> changes) {}
    public record Change(Value value) {}
    public record Value(List<Message> messages) {}
    public record Message(String from, String type, Text text, Audio audio, Interactive interactive) {}
    public record Text(String body) {}
    public record Audio(String id, @JsonProperty("mime_type") String mimeType) {}
    public record Interactive(
            String type,
            @JsonProperty("button_reply") ButtonReply buttonReply
    ) {}
    public record ButtonReply(String id, String title) {}
    public record AudioMessage(String from, String mediaId, String mimeType) {}
    public record InteractiveMessage(String from, String buttonId) {}
}
