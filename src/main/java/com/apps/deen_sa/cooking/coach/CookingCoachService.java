package com.apps.deen_sa.cooking.coach;

import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.cooking.recipe.Recipe;
import com.apps.deen_sa.cooking.recipe.RecipeCatalog;
import com.apps.deen_sa.cooking.recipe.RecipeScaler;
import com.apps.deen_sa.cooking.session.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CookingCoachService {
    private static final String RECIPE_ID = "chicken-biryani";
    private static final String UNIT = "(kg|g|grm|grms|gram|grams)?";
    private static final Pattern RICE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*" + UNIT + "\\s*(?:of\\s+)?rice", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHICKEN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*" + UNIT + "\\s*(?:of\\s+)?chicken", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHARED = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*" + UNIT + "\\s*(?:of\\s+)?rice\\s+and\\s+chicken", Pattern.CASE_INSENSITIVE);
    private final RecipeCatalog recipes;
    private final RecipeScaler scaler;
    private final CookingSessionService sessions;
    private final CookingSetupService setup;
    private final CookingAdviceService advice;

    public SpeechResult process(Long userId, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        String command = text.toLowerCase(Locale.ROOT);
        Optional<CookingSessionEntity> active = sessions.active(userId);
        if (isStart(command)) return SpeechResult.info(setup.begin(userId));
        Optional<CookingSetupEntity> activeSetup = setup.active(userId);
        if (activeSetup.isPresent()) {
            CookingSetupService.SetupReply reply = setup.answer(activeSetup.get(), text);
            if (!reply.complete()) return SpeechResult.info(reply.message());
            Recipe selectedRecipe = recipes.require(RECIPE_ID);
            CookingSetupService.SetupSelection selected = reply.selection();
            CookingSessionEntity created = sessions.start(userId, selectedRecipe, selected.riceGrams(),
                    selected.chickenGrams(), selected.riceType(), selected.equipment());
            return SpeechResult.info("Setup complete for " + selected.riceGrams().stripTrailingZeros().toPlainString()
                    + " g Basmati rice and " + selected.chickenGrams().stripTrailingZeros().toPlainString()
                    + " g chicken in a biryani pot.\n\n" + ingredients(selectedRecipe, created)
                    + "\n\nPrepare everything, then reply ‘ready’.");
        }
        if (active.isEmpty()) return SpeechResult.info("Welcome to Cooking Coach. Say ‘start chicken biryani with 500 g rice and 500 g chicken’ to begin.");
        CookingSessionEntity session = active.get();
        Recipe recipe = recipes.require(session.getRecipeId());
        if (matches(command, "ingredients", "ingredient list", "shopping list")) return SpeechResult.info(ingredients(recipe, session));
        if (matches(command, "begin", "ready", "start cooking", "cook")) return begin(recipe, session);
        if (matches(command, "both ready", "all ready", "rice and gravy ready")) return next(recipe, session);
        if (matches(command, "next", "done", "continue")) {
            Recipe.RecipeStep current = recipe.steps().get(session.getCurrentStep());
            if (current.parallelTasks() != null && !current.parallelTasks().isEmpty())
                return SpeechResult.followup(parallelReadinessQuestion(current),
                        java.util.List.of("parallelTasksReady"), null);
            return next(recipe, session);
        }
        if (matches(command, "repeat", "again", "current step")) return SpeechResult.info(step(recipe, session));
        if (matches(command, "progress", "where am i", "status")) return SpeechResult.info(progress(recipe, session));
        if (matches(command, "pause")) { session.setStatus(CookingSessionStatus.PAUSED); sessions.save(session); return SpeechResult.info("Paused at step " + (session.getCurrentStep() + 1) + ". Say ‘resume’ when ready."); }
        if (matches(command, "resume")) { session.setStatus(CookingSessionStatus.COOKING); sessions.save(session); return SpeechResult.info("Resumed.\n\n" + step(recipe, session)); }
        if (matches(command, "cancel", "stop cooking")) { session.setStatus(CookingSessionStatus.CANCELLED); sessions.save(session); return SpeechResult.info("Cooking session cancelled. Say ‘start chicken biryani…’ whenever you want to begin again."); }
        if (matches(command, "help")) return SpeechResult.info("Try: ingredients, ready, done, repeat, progress, pause, resume, cancel—or describe a doubt or mistake in your own words.");
        String recovery = deterministicRecovery(command, recipe, session);
        if (recovery != null) return SpeechResult.info(recovery);
        return SpeechResult.info(advice.answer(text, recipe, session));
    }

    private SpeechResult begin(Recipe recipe, CookingSessionEntity session) {
        session.setStatus(CookingSessionStatus.COOKING); sessions.save(session);
        return SpeechResult.info("Let’s cook.\n\n" + step(recipe, session));
    }

    private SpeechResult next(Recipe recipe, CookingSessionEntity session) {
        if (session.getStatus() == CookingSessionStatus.PREPARING) return begin(recipe, session);
        if (session.getStatus() == CookingSessionStatus.PAUSED) return SpeechResult.info("Your session is paused. Say ‘resume’ first.");
        if (session.getCurrentStep() + 1 >= recipe.steps().size()) {
            session.setStatus(CookingSessionStatus.COMPLETED); sessions.save(session);
            return SpeechResult.info("Biryani complete. After the 10-minute covered rest, verify the thickest chicken piece is 74°C/165°F away from bone. Then transfer gently to a large vessel and lift sections just enough to combine the concentrated bottom gravy and oil with the top rice. Do not stir in circles.");
        }
        session.setCurrentStep(session.getCurrentStep() + 1); sessions.save(session);
        return SpeechResult.info(step(recipe, session));
    }

    private String step(Recipe recipe, CookingSessionEntity session) {
        Recipe.RecipeStep value = recipe.steps().get(session.getCurrentStep());
        String message = "Step " + (session.getCurrentStep() + 1) + "/" + recipe.steps().size() + " — " + value.stage() + "\n" + value.instruction();
        if (value.checkpoint() != null) message += "\nLook for: " + value.checkpoint();
        if (value.parallelTasks() != null && !value.parallelTasks().isEmpty()) {
            message += "\n\nCoordinate in parallel (one action at a time):";
            for (Recipe.RecipeStep.ParallelTask task : value.parallelTasks()) {
                message += "\n• " + task.name() + " — Start when: " + task.startWhen()
                        + "\n  Do: " + task.instruction()
                        + "\n  Ready when: " + task.readyWhen();
                if (task.ifReadyEarly() != null && !task.ifReadyEarly().isBlank())
                    message += "\n  If ready early: " + task.ifReadyEarly();
            }
            message += "\nAdvance only when the primary step and every parallel task are ready.";
        }
        if (value.warning() != null) message += "\n⚠️ " + value.warning();
        return message + "\n\nReply ‘done’, ‘repeat’, or ask a question.";
    }

    private String parallelReadinessQuestion(Recipe.RecipeStep step) {
        String tasks = step.parallelTasks().stream().map(Recipe.RecipeStep.ParallelTask::name)
                .collect(java.util.stream.Collectors.joining(" and "));
        return "Before advancing, confirm that the primary step and " + tasks
                + " have each reached their Ready when checkpoint. Reply ‘both ready’ when they are synchronized, or tell me which one is early or delayed.";
    }

    private String ingredients(Recipe recipe, CookingSessionEntity session) {
        StringBuilder out = new StringBuilder("Ingredients:");
        scaler.scale(recipe, session.getRiceGrams(), session.getChickenGrams()).forEach(item -> {
            out.append("\n• ").append(item.name()).append(": ").append(item.amount().toPlainString()).append(" ").append(item.unit());
            if (item.note() != null && !item.note().isBlank()) out.append(" (").append(item.note()).append(")");
        });
        return out.toString();
    }

    private String progress(Recipe recipe, CookingSessionEntity session) {
        return "Chicken biryani v" + session.getRecipeVersion() + ": " + session.getStatus().name().toLowerCase(Locale.ROOT)
                + ", step " + (session.getCurrentStep() + 1) + " of " + recipe.steps().size() + ".\n" + step(recipe, session);
    }

    private String deterministicRecovery(String command, Recipe recipe, CookingSessionEntity session) {
        for (Recipe.RecoveryRule rule : recipe.recoveryRules()) {
            if (rule.keywords().stream().anyMatch(command::contains)) {
                String note = rule.problem() + ": " + rule.action();
                session.setAdjustmentNotes(append(session.getAdjustmentNotes(), note)); sessions.save(session);
                return "Act now: " + rule.action() + (rule.safetyNote() == null ? "" : "\n⚠️ " + rule.safetyNote())
                        + "\n\nI’ve remembered this adjustment for the remaining steps.";
            }
        }
        return null;
    }

    private String append(String existing, String note) { return existing == null || existing.isBlank() ? note : existing + "\n" + note; }
    private boolean isStart(String command) { return command.contains("start") && (command.contains("biryani") || command.contains("briyani")); }
    private boolean matches(String value, String... commands) { for (String command : commands) if (value.equals(command)) return true; return false; }
    private BigDecimal quantity(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text); if (!matcher.find()) return null;
        BigDecimal value = new BigDecimal(matcher.group(1));
        return "kg".equalsIgnoreCase(matcher.group(2)) ? value.multiply(new BigDecimal("1000")) : value;
    }
}
