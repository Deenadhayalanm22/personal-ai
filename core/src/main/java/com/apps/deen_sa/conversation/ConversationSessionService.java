package com.apps.deen_sa.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConversationSessionService {
    private final ConversationSessionRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ConversationContext load(Long userId, String channel) {
        ConversationSessionEntity session = repository.findByUserIdAndChannel(userId, channel).orElse(null);
        ConversationContext context = new ConversationContext();
        context.setUserId(userId);
        context.setChannel(channel);
        if (session == null) return context;
        context.setSessionId(session.getId());
        context.setActiveTransactionId(session.getActiveTransactionId());
        context.setActiveIntent(session.getActiveIntent());
        context.setWaitingForField(session.getWaitingForField());
        context.setPendingEvents(session.getPendingEvents() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(session.getPendingEvents()));
        context.setRecentTurns(session.getRecentTurns() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(session.getRecentTurns()));
        context.setLastQuestion(session.getLastQuestion());
        context.setInterpreterVersion(session.getInterpreterVersion());
        if (session.getPartialJson() != null && session.getPartialType() != null) {
            try {
                Class<?> type = Class.forName(session.getPartialType());
                context.setPartialObject(objectMapper.convertValue(session.getPartialJson(), type));
            } catch (ClassNotFoundException exception) {
                context.reset();
            }
        }
        return context;
    }

    @Transactional
    public void save(ConversationContext context) {
        ConversationSessionEntity session = context.getSessionId() == null
                ? repository.findByUserIdAndChannel(context.getUserId(), context.getChannel())
                    .orElseGet(ConversationSessionEntity::new)
                : repository.findById(context.getSessionId()).orElseGet(ConversationSessionEntity::new);
        session.setUserId(context.getUserId());
        session.setChannel(context.getChannel());
        session.setActiveTransactionId(context.getActiveTransactionId());
        session.setActiveIntent(context.getActiveIntent());
        session.setWaitingForField(context.getWaitingForField());
        session.setPendingEvents(context.getPendingEvents());
        session.setRecentTurns(context.getRecentTurns());
        session.setLastQuestion(context.getLastQuestion());
        session.setInterpreterVersion(context.getInterpreterVersion());
        Object partial = context.getPartialObject();
        session.setPartialType(partial == null ? null : partial.getClass().getName());
        session.setPartialJson(partial == null ? null : objectMapper.convertValue(partial, Map.class));
        session.setUpdatedAt(Instant.now());
        ConversationSessionEntity saved = repository.save(session);
        context.setSessionId(saved.getId());
    }

    public void clearAll() {
        repository.deleteAll();
    }
}
