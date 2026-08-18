package com.apps.deen_sa.cooking.recipe;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeScalerTest {
    private final RecipeScaler scaler = new RecipeScaler();

    @Test
    void scalesRiceAndChickenIngredientsIndependentlyAndKeepsFixedSpices() {
        Recipe recipe = new Recipe("test", 1, "Test", bd("500"), bd("500"), List.of(
                ingredient("Rice", "500", Recipe.ScaleBy.RICE),
                ingredient("Curd", "100", Recipe.ScaleBy.CHICKEN),
                ingredient("Bay leaf", "2", Recipe.ScaleBy.FIXED)), List.of(), List.of());

        List<RecipeScaler.ScaledIngredient> result = scaler.scale(recipe, bd("750"), bd("1000"));

        assertThat(result).extracting(item -> item.amount().toPlainString())
                .containsExactly("750", "200", "2");
    }

    private Recipe.Ingredient ingredient(String name, String amount, Recipe.ScaleBy scaleBy) {
        return new Recipe.Ingredient(name, bd(amount), "g", scaleBy, null);
    }
    private BigDecimal bd(String value) { return new BigDecimal(value); }
}
