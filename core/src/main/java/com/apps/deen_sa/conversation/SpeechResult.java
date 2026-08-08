package com.apps.deen_sa.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpeechResult {

    private SpeechStatus status;

    // Response text to show user
    private String message;

    // When handler needs another field from the user
    private Boolean needFollowup;

    // List of missing fields backend expects next
    private List<String> missingFields;

    // Extension-owned partial facts retained across a follow-up.
    private Object partial;

    // Final saved entity (any handler type)
    private Object savedEntity;

    // Optional: Used by orchestrator to know next handler or context
    private String nextAction;

    private List<ResponseAction> actions;

    // ---------- FACTORY METHODS ---------- //

    public static SpeechResult invalid(String reason) {
        return SpeechResult.builder()
                .status(SpeechStatus.INVALID)
                .message(reason)
                .needFollowup(false)
                .build();
    }

    public static SpeechResult followup(String question, List<String> fields, Object partial) {
        return SpeechResult.builder()
                .status(SpeechStatus.FOLLOWUP)
                .message(question)
                .needFollowup(true)
                .missingFields(fields)
                .partial(partial)
                .build();
    }

    public static SpeechResult followup(String question, List<String> fields, Object partial,
                                        List<ResponseAction> actions) {
        return SpeechResult.builder()
                .status(SpeechStatus.FOLLOWUP)
                .message(question)
                .needFollowup(true)
                .missingFields(fields)
                .partial(partial)
                .actions(actions)
                .build();
    }

    public static SpeechResult saved(Object entity) {
        return SpeechResult.builder()
                .status(SpeechStatus.SAVED)
                .message("Saved successfully.")
                .savedEntity(entity)
                .needFollowup(false)
                .build();
    }

    public static SpeechResult unknown(String msg) {
        return SpeechResult.builder()
                .status(SpeechStatus.UNKNOWN)
                .message(msg)
                .needFollowup(false)
                .build();
    }

    public static SpeechResult info(String msg) {
        return SpeechResult.builder()
                .status(SpeechStatus.INFO)
                .message(msg)
                .build();
    }
}
