package com.apps.deen_sa.cooking.recipe;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RecipeScaler {
    public List<ScaledIngredient> scale(Recipe recipe, BigDecimal riceGrams, BigDecimal chickenGrams) {
        BigDecimal riceFactor = riceGrams.divide(recipe.baseRiceGrams(), 4, RoundingMode.HALF_UP);
        BigDecimal chickenFactor = chickenGrams.divide(recipe.baseChickenGrams(), 4, RoundingMode.HALF_UP);
        return recipe.ingredients().stream().map(ingredient -> {
            BigDecimal factor = switch (ingredient.scaleBy()) {
                case RICE -> riceFactor;
                case CHICKEN -> chickenFactor;
                case FIXED -> BigDecimal.ONE;
            };
            BigDecimal amount = ingredient.amount().multiply(factor).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
            return new ScaledIngredient(ingredient.name(), amount, ingredient.unit(), ingredient.note());
        }).toList();
    }

    public record ScaledIngredient(String name, BigDecimal amount, String unit, String note) { }
}
