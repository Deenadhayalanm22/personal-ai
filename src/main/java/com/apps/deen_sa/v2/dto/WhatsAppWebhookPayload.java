package com.apps.deen_sa.v2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record WhatsAppWebhookPayload(List<Entry> entry) {
    public record Entry(List<Change> changes) {}
    public record Change(Value value) {}
    public record Value(List<Message> messages) {}
    public record Message(
            String id,
            String from,
            String type,
            Text text,
            Audio audio,
            Interactive interactive
    ) {
        public Message(String id, String from, String type, Text text, Audio audio) {
            this(id, from, type, text, audio, null);
        }
    }
    public record Text(String body) {}
    public record Audio(String id, @JsonProperty("mime_type") String mimeType) {}
    public record Interactive(
            @JsonProperty("button_reply") Reply buttonReply,
            @JsonProperty("list_reply") Reply listReply
    ) {
        public String replyId() {
            return buttonReply != null ? buttonReply.id() : listReply != null ? listReply.id() : null;
        }
    }
    public record Reply(String id, String title) {}
}
