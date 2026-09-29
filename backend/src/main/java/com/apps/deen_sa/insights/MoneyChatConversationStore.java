package com.apps.deen_sa.insights;

import com.apps.deen_sa.exception.WebApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** FIN-EPIC-003: profile-owned, bounded storage for V1 money conversations. */
@Service
public class MoneyChatConversationStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MoneyChatConversationStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Conversation> list(long userId) {
        return jdbc.query("""
                SELECT id, title, month, draft, messages::text
                FROM money_chat_conversation WHERE user_id = ?
                ORDER BY updated_at DESC, created_at DESC LIMIT 50
                """, (rs, row) -> new Conversation(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), parse(rs.getString(5))), userId);
    }

    public Conversation put(long userId, UUID id, Conversation input) {
        validate(id, input);
        String messages = input.messages().toString();
        jdbc.update("""
                INSERT INTO money_chat_conversation (user_id, id, title, month, draft, messages)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (user_id, id) DO UPDATE SET title = EXCLUDED.title, month = EXCLUDED.month,
                    draft = EXCLUDED.draft, messages = EXCLUDED.messages, updated_at = now()
                """, userId, id, input.title().trim(), input.month(), input.draft(), messages);
        return new Conversation(id.toString(), input.title().trim(), input.month(), input.draft(), input.messages());
    }

    private void validate(UUID id, Conversation input) {
        if (input == null || input.id() == null || !id.toString().equals(input.id()) || input.title() == null
                || input.title().isBlank() || input.title().length() > 200 || input.draft() == null
                || input.draft().length() > 2000 || input.messages() == null || !input.messages().isArray()
                || input.messages().size() > 100 || input.messages().toString().length() > 500_000)
            throw invalid();
        try { YearMonth.parse(input.month()); } catch (DateTimeException | NullPointerException ex) { throw invalid(); }
        for (JsonNode message : input.messages()) {
            if (!message.isObject() || !message.path("role").isTextual()
                    || !List.of("user", "assistant").contains(message.path("role").asText())
                    || !message.path("content").isTextual() || message.path("content").asText().length() > 12000
                    || (message.has("evidence") && !message.path("evidence").isArray())) throw invalid();
        }
    }

    private JsonNode parse(String value) {
        try { return mapper.readTree(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Invalid stored conversation", ex); }
    }

    private static WebApiException invalid() {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_CHAT_CONVERSATION", "Invalid money conversation.");
    }

    public record Conversation(String id, String title, String month, String draft, JsonNode messages) {}
}
