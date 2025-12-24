package com.apps.deen_sa.dto;

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
                .filter(m -> "text".equals(m.type()))
                .map(m -> new UserMessage(
                        m.from(),
                        m.text().body()
                ))
                .toList();
    }

    public record Entry(List<Change> changes) {}
    public record Change(Value value) {}
    public record Value(List<Message> messages) {}
    public record Message(String from, String type, Text text) {}
    public record Text(String body) {}
}
