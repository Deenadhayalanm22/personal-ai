package com.apps.deen_sa.finance.presentation;

public record FinancialPresentationRequest(
        AnalysisIntent intent,
        PresentationMood mood,
        double intentConfidence,
        double moodConfidence
) {
    public static FinancialPresentationRequest fromAi(String intent, String mood) {
        return new FinancialPresentationRequest(parseIntent(intent), parseMood(mood),
                intent == null ? 0 : 1, mood == null ? 0 : 1);
    }

    private static AnalysisIntent parseIntent(String value) {
        if (value == null || value.isBlank()) return AnalysisIntent.SPENDING_OVERVIEW;
        try { return AnalysisIntent.valueOf(value); }
        catch (IllegalArgumentException ignored) { return AnalysisIntent.SPENDING_OVERVIEW; }
    }

    private static PresentationMood parseMood(String value) {
        if (value == null || value.isBlank()) return PresentationMood.NEUTRAL;
        try { return PresentationMood.valueOf(value); }
        catch (IllegalArgumentException ignored) { return PresentationMood.NEUTRAL; }
    }
}
