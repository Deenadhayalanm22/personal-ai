package com.apps.deen_sa.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class ConversationDiagnosticService {
    private final ConversationDiagnosticTurnRepository repository;
    private final ObjectMapper objectMapper;

    /** Diagnostics must never make an otherwise successful customer turn fail. */
    public void record(String inputKind, String externalUserId, String externalMessageId,
                       String inputText, ConversationContext context, SpeechResult result) {
        try {
            ConversationDiagnosticTurnEntity turn = new ConversationDiagnosticTurnEntity();
            turn.setUserId(context.getUserId());
            turn.setChannel(context.getChannel());
            turn.setExternalUserId(externalUserId);
            turn.setExternalMessageId(externalMessageId);
            turn.setInputKind(inputKind);
            turn.setInputText(inputText);
            turn.setResponseStatus(result.getStatus() == null ? null : result.getStatus().name());
            turn.setResponseText(result.getMessage());
            turn.setNeedFollowup(result.getNeedFollowup());
            turn.setActiveIntent(context.getActiveIntent());
            turn.setWaitingForField(context.getWaitingForField());
            turn.setPartialJson(json(context.getPartialObject()));
            Object saved = result.getSavedEntity();
            turn.setSavedEntityType(saved == null ? null : saved.getClass().getName());
            turn.setSavedEntityJson(json(saved));
            repository.saveAndFlush(turn);
        } catch (RuntimeException failure) {
            log.warn("Could not record conversation diagnostic turn {}", externalMessageId, failure);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(Object value) {
        return value == null ? null : objectMapper.convertValue(value, Map.class);
    }
}
