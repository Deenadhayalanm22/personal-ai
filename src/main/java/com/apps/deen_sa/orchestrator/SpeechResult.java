package com.apps.deen_sa.orchestrator;

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

    // Store partial DTO (expense, investment, account, etc.)
    private Object partial;

    // Final saved entity (any handler type)
    private Object savedEntity;

    // Optional: Used by orchestrator to know next handler or context
    private String nextAction;

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
