package com.apps.deen_sa.v2.dto;

public record ExpenseConfirmationCommand(
        String externalUserId,
        long extractionId,
        Action action
) {
    public enum Action {
        CONFIRM,
        DISCARD
    }
}
