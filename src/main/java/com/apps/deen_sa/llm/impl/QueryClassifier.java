package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.registry.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.dto.QueryResult;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.apps.deen_sa.llm.PromptLoader;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

@Service
public class QueryClassifier extends BaseLLMExtractor {

    private final ExpenseTaxonomyRegistry expenseTaxonomyRegistry;
    private final String systemPromptTemplate;

    protected QueryClassifier(
            OpenAIClient client,
            PromptLoader promptLoader,
            ExpenseTaxonomyRegistry expenseTaxonomyRegistry
    ) {
        super(client);
        this.expenseTaxonomyRegistry = expenseTaxonomyRegistry;

        this.systemPromptTemplate = promptLoader.combine(
                "llm/common/global_rules.md",
                "llm/query/classify.md",
                "llm/query/schema.json"
        );
    }

    public QueryResult classify(String text) {
        String systemPrompt = systemPromptTemplate.formatted(
                renderTaxonomy(expenseTaxonomyRegistry),
                text
        );

        return callAndParse(
                systemPrompt,
                text,
                QueryResult.class
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
}
