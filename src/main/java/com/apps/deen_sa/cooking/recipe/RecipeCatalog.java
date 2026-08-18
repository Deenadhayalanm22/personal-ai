package com.apps.deen_sa.cooking.recipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecipeCatalog {
    private final ObjectMapper objectMapper;
    private Map<String, Recipe> recipes;

    @PostConstruct
    void load() throws IOException {
        Recipe recipe = objectMapper.readValue(
                new ClassPathResource("recipes/chicken-biryani-v1.json").getInputStream(), Recipe.class);
        recipes = Map.of(recipe.id(), recipe);
    }

    public Recipe require(String id) {
        Recipe recipe = recipes.get(id);
        if (recipe == null) throw new IllegalArgumentException("Unknown recipe: " + id);
        return recipe;
    }
}
