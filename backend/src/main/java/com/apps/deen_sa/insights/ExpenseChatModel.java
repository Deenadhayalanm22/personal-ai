package com.apps.deen_sa.insights;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface ExpenseChatModel {
    Reply complete(String system, List<Message> messages, JsonNode tool);
    record Call(String id, String name, String arguments) {}
    record Message(String role, String content, String toolCallId, List<Call> calls) {
        public Message(String role, String content) { this(role, content, null, List.of()); }
    }
    record Reply(String text, List<Call> calls) {}
}
