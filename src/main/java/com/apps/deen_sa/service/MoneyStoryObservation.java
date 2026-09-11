package com.apps.deen_sa.service;

import java.math.BigDecimal;
import java.util.List;

/** Auditable facts behind a one-card observation. No inferred habits or financial advice. */
public record MoneyStoryObservation(Kind kind, String subject, String merchantLabel,
        BigDecimal total, BigDecimal focusAmount, BigDecimal otherAmount, BigDecimal sharePercent,
        int count, int focusCount, int activeDays, List<Part> parts,
        List<Long> evidenceIds, List<Long> focusTransactionIds,
        int incompleteClassificationCount, int possibleRepeatCount, String noveltyKey) {
    public enum Kind { CONTRIBUTOR, BREAKDOWN, PURPOSE, REPEAT, SUMMARY }
    public record Part(String label, BigDecimal amount, int count) { }

    public int usefulness() {
        int score = switch (kind) {
            case CONTRIBUTOR -> 100;
            case BREAKDOWN -> 85;
            case PURPOSE -> 70;
            case REPEAT -> 65;
            case SUMMARY -> 10;
        };
        return score - (possibleRepeatCount > 0 ? 40 : 0);
    }
}
