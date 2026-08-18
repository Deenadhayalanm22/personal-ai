package com.apps.deen_sa.cooking.coach;

import com.apps.deen_sa.cooking.recipe.Recipe;
import com.apps.deen_sa.cooking.session.CookingSessionEntity;
import com.apps.deen_sa.llm.AiCallTelemetry;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CookingAdviceService {
    private final OpenAIClient client;
    @Value("${openai.model:gpt-4.1-mini}") private String model;

    public String answer(String question, Recipe recipe, CookingSessionEntity session) {
        long started = System.nanoTime();
        String current = recipe.steps().get(Math.min(session.getCurrentStep(), recipe.steps().size() - 1)).toString();
        String instructions = """
                You are a concise WhatsApp cooking coach. Ground your answer only in the supplied curated recipe,
                current step, and known recovery rules. Never change quantities silently. Ask one focused question
                if the observation is insufficient. Put urgent action first. Then give heat/time/quantity, what to
                observe, and when to reply. For uncertain chicken doneness recommend a food thermometer; never
                guarantee food safety from appearance. Keep the answer under 120 words.
                """;
        String input = "Recipe: " + recipe + "\nRice grams: " + session.getRiceGrams()
                + "\nChicken grams: " + session.getChickenGrams() + "\nCurrent step: " + current
                + "\nPrior adjustments: " + session.getAdjustmentNotes() + "\nUser: " + question;
        try {
            Response response = client.responses().create(ResponseCreateParams.builder()
                    .model(model).instructions(instructions).input(input).build());
            response.usage().ifPresentOrElse(usage -> AiCallTelemetry.success("cooking_advice", model,
                            usage.inputTokens(), usage.inputTokensDetails().cachedTokens(), usage.outputTokens(), started),
                    () -> AiCallTelemetry.success("cooking_advice", model, 0, 0, 0, started));
            return response.output().stream().flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                    .map(ResponseOutputText::text).findFirst()
                    .orElse("I need a little more detail. What do you see, smell, and feel right now?");
        } catch (Exception exception) {
            AiCallTelemetry.failure("cooking_advice", model, started);
            return "I couldn't check that safely right now. Pause the heat if food is burning, and tell me what you see, smell, and which step you are on.";
        }
    }
}
