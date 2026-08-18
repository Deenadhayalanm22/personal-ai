package com.apps.deen_sa.cooking.recipe;

import java.math.BigDecimal;
import java.util.List;

public record Recipe(
        String id,
        int version,
        String name,
        BigDecimal baseRiceGrams,
        BigDecimal baseChickenGrams,
        List<Ingredient> ingredients,
        List<RecipeStep> steps,
        List<RecoveryRule> recoveryRules
) {
    public record Ingredient(String name, BigDecimal amount, String unit, ScaleBy scaleBy, String note) { }
    public record RecipeStep(String stage, String instruction, String checkpoint, String warning,
                             List<ParallelTask> parallelTasks) {
        public record ParallelTask(String name, String startWhen, String instruction,
                                   String readyWhen, String ifReadyEarly) { }
    }
    public record RecoveryRule(String problem, List<String> keywords, String action, String safetyNote) { }
    public enum ScaleBy { RICE, CHICKEN, FIXED }
}
