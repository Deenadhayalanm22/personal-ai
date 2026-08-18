package com.apps.deen_sa.cooking.coach;

import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.cooking.recipe.*;
import com.apps.deen_sa.cooking.session.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CookingCoachServiceTest {
    @Test
    void startsGuidedSetupInsteadOfRequiringQuantitiesInFirstMessage() {
        RecipeCatalog catalog = mock(RecipeCatalog.class);
        CookingSessionService sessions = mock(CookingSessionService.class);
        CookingSetupService setup = mock(CookingSetupService.class);
        when(sessions.active(7L)).thenReturn(Optional.empty());
        when(setup.begin(7L)).thenReturn("Are you ready to prepare the chicken biryani? Reply Yes or No.");
        CookingCoachService coach = new CookingCoachService(catalog, new RecipeScaler(), sessions, setup,
                mock(CookingAdviceService.class));

        SpeechResult result = coach.process(7L, "start chicken briyani");

        verify(setup).begin(7L);
        assertThat(result.getMessage()).contains("Are you ready").contains("Yes or No");
    }

    @Test
    void routesNaturalStickingObservationToApprovedRecoveryWithoutCallingAi() {
        RecipeCatalog catalog = mock(RecipeCatalog.class);
        CookingSessionService sessions = mock(CookingSessionService.class);
        CookingAdviceService advice = mock(CookingAdviceService.class);
        Recipe recipe = new Recipe("chicken-biryani", 1, "Chicken Biryani",
                new BigDecimal("500"), new BigDecimal("500"), List.of(), List.of(
                new Recipe.RecipeStep("Gravy", "Cook gravy", "Formed", null, List.of())), List.of(
                new Recipe.RecoveryRule("Onion-tomato base catching",
                        List.of("masala is sticking", "sticking at the bottom"),
                        "Reduce heat and move the unstuck gravy to a clean pot without scraping the burnt base.",
                        "Handle the hot pot carefully.")));
        CookingSessionEntity active = new CookingSessionEntity();
        active.setRecipeId("chicken-biryani"); active.setRecipeVersion(1);
        active.setRiceGrams(new BigDecimal("500")); active.setChickenGrams(new BigDecimal("500"));
        active.setStatus(CookingSessionStatus.COOKING); active.setCurrentStep(0);
        when(sessions.active(7L)).thenReturn(Optional.of(active));
        when(catalog.require("chicken-biryani")).thenReturn(recipe);
        CookingCoachService coach = new CookingCoachService(catalog, new RecipeScaler(), sessions,
                mock(CookingSetupService.class), advice);

        SpeechResult result = coach.process(7L, "The masala is sticking at the bottom. What should I do?");

        assertThat(result.getMessage()).contains("Act now").contains("clean pot").contains("remembered this adjustment");
        assertThat(active.getAdjustmentNotes()).contains("Onion-tomato base catching");
        verify(sessions).save(active);
        verifyNoInteractions(advice);
    }

    @Test
    void parallelPhaseRequiresExplicitReadinessBeforeAdvancing() {
        RecipeCatalog catalog = mock(RecipeCatalog.class);
        CookingSessionService sessions = mock(CookingSessionService.class);
        Recipe.RecipeStep coordinated = new Recipe.RecipeStep("Coordinate", "Cook gravy", "Gravy ready", null,
                List.of(new Recipe.RecipeStep.ParallelTask("Rice pot", "Start now", "Parboil rice",
                        "Rice is 50% cooked", "Drain immediately")));
        Recipe recipe = new Recipe("chicken-biryani", 1, "Chicken Biryani", new BigDecimal("500"),
                new BigDecimal("500"), List.of(), List.of(coordinated,
                new Recipe.RecipeStep("Combine", "Add rice", "Combined", null, List.of())), List.of());
        CookingSessionEntity active = new CookingSessionEntity();
        active.setRecipeId("chicken-biryani"); active.setRecipeVersion(1);
        active.setRiceGrams(new BigDecimal("500")); active.setChickenGrams(new BigDecimal("500"));
        active.setStatus(CookingSessionStatus.COOKING); active.setCurrentStep(0);
        when(sessions.active(7L)).thenReturn(Optional.of(active));
        when(catalog.require("chicken-biryani")).thenReturn(recipe);
        CookingCoachService coach = new CookingCoachService(catalog, new RecipeScaler(), sessions,
                mock(CookingSetupService.class), mock(CookingAdviceService.class));

        SpeechResult blocked = coach.process(7L, "done");
        assertThat(blocked.getMessage()).contains("Before advancing").contains("Rice pot").contains("both ready");
        assertThat(active.getCurrentStep()).isZero();

        SpeechResult advanced = coach.process(7L, "both ready");
        assertThat(active.getCurrentStep()).isOne();
        assertThat(advanced.getMessage()).contains("Step 2/2").contains("Combine");
        verify(sessions).save(active);
    }
}
