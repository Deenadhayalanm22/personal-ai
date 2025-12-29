package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.registry.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.exception.LLMParsingException;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.apps.deen_sa.llm.PromptLoader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class ExpenseClassifier extends BaseLLMExtractor {

    private final ExpenseTaxonomyRegistry expenseTaxonomyRegistry;
    private final PromptLoader promptLoader;
    private final String behaviorPrompt;

    protected ExpenseClassifier(OpenAIClient client,
                                PromptLoader promptLoader,
                                ExpenseTaxonomyRegistry expenseTaxonomyRegistry) {
        super(client);
        this.promptLoader = promptLoader;
        this.expenseTaxonomyRegistry = expenseTaxonomyRegistry;
        this.behaviorPrompt = buildBehaviorPrompt();
    }

    // ---------------------------------------------
    // SYSTEM BEHAVIOR PROMPT (reused for all calls)
    // ---------------------------------------------
    private String buildBehaviorPrompt() {
        String today = OffsetDateTime.now().toLocalDate().toString();

        String categoryTypesTemplate = promptLoader.load("llm/expense/category_types.md");
        String rulesTemplate = promptLoader.load("llm/expense/rules.md");
        String schemaTemplate = promptLoader.load("llm/expense/schema.json");

        String combinedPrompt = promptLoader.combine(
                "llm/common/global_rules.md",
                "llm/expense/category_types.md",
                "llm/expense/rules.md",
                "llm/expense/schema.json"
        );

        return combinedPrompt.formatted(
                today,
                renderTaxonomy(expenseTaxonomyRegistry)
        );
    }

    private String renderTaxonomy(ExpenseTaxonomyRegistry registry) {
        StringBuilder sb = new StringBuilder();
        registry.categories().forEach(cat -> {
            sb.append("- ").append(cat).append(":\n");
            registry.subcategoriesFor(cat)
                    .forEach(sub -> sb.append("  - ").append(sub).append("\n"));
        });
        return sb.toString();
    }

    // ---------------------------------------------
    // MAIN EXPENSE EXTRACTION
    // ---------------------------------------------
    public ExpenseDto extractExpense(String spokenText) {
        String extractTemplate = promptLoader.load("llm/expense/extract.md");
        String userPrompt = extractTemplate.formatted(spokenText);
        
        return callAndParse(
                behaviorPrompt,
                userPrompt,
                ExpenseDto.class
        );
    }

    // ---------------------------------------------
    // FOLLOW-UP FIELD REFINEMENT
    // ---------------------------------------------
    public ExpenseDto extractFieldFromFollowup(ExpenseDto existing,
                                               String missingField,
                                               String userAnswer) {
        try {
            String followupTemplate = promptLoader.load("llm/expense/followup_refinement.md");
            String followPrompt = followupTemplate.formatted(
                    mapper.writeValueAsString(existing),
                    missingField,
                    userAnswer
            );

            return callAndParse(
                    behaviorPrompt,
                    followPrompt,
                    ExpenseDto.class
            );
        } catch (JsonProcessingException e) {
            throw new LLMParsingException("Failed to parse LLM JSON response", e);
        }
    }

    // ---------------------------------------------
    // FOLLOW-UP QUESTION GENERATION
    // ---------------------------------------------
    public String generateFollowupQuestionForExpense(String missingField, ExpenseDto dto) {
        String questionTemplate = promptLoader.load("llm/expense/followup_question.md");
        String prompt = questionTemplate.formatted(dto, missingField);

        return callLLM(behaviorPrompt, prompt);
    }
}
