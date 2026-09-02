package com.apps.deen_sa.v2.normalization;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.apps.deen_sa.v2.domain.UserReferenceEntityType;
import com.apps.deen_sa.v2.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.v2.repository.UserReferenceEntityRepository;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class AiExpenseNormalizationAdapter extends BaseLLMExtractor
        implements ExpenseNormalizationPort {
    private final ExpenseTaxonomyRegistry taxonomy;
    private final UserReferenceEntityRepository referenceRepository;
    private final UserReferenceAliasRepository aliasRepository;

    public AiExpenseNormalizationAdapter(
            OpenAIClient client,
            ApplicationProperties properties,
            ExpenseTaxonomyRegistry taxonomy,
            UserReferenceEntityRepository referenceRepository,
            UserReferenceAliasRepository aliasRepository
    ) {
        super(client, properties);
        this.taxonomy = taxonomy;
        this.referenceRepository = referenceRepository;
        this.aliasRepository = aliasRepository;
    }

    @Override
    public ExpenseFacts normalize(String externalUserId, String rawText, LocalDate today) {
        ExpenseFacts extracted = callAndParse(
                systemPrompt(today, externalUserId),
                "Normalize this expense message:\n" + rawText,
                ExpenseFacts.class);
        return enforceTaxonomy(extracted, today);
    }

    private ExpenseFacts enforceTaxonomy(ExpenseFacts extracted, LocalDate today) {
        if (extracted == null) {
            throw new IllegalStateException("Expense normalization returned no data");
        }

        String extractedCategory = taxonomy.canonicalLabel(extracted.category()).orElse(null);
        String subcategory = taxonomy.canonicalLabel(extracted.subcategory())
                .filter(value -> extractedCategory != null
                        && taxonomy.subcategoriesFor(extractedCategory).contains(value))
                .orElse(null);
        String category = subcategory == null
                ? extractedCategory
                : taxonomy.parentCategory(subcategory).orElse(extractedCategory);

        return new ExpenseFacts(
                extracted.amount(),
                category,
                subcategory,
                blankToNull(extracted.merchant()),
                extracted.transactionDate() == null ? today : extracted.transactionDate(),
                validConfidence(extracted.confidence()));
    }

    private String systemPrompt(LocalDate today, String externalUserId) {
        StringBuilder configuredTaxonomy = new StringBuilder();
        taxonomy.categories().forEach(category -> {
            configuredTaxonomy.append("- ").append(category).append(":\n");
            taxonomy.subcategoriesFor(category).forEach(subcategory ->
                    configuredTaxonomy.append("  - ").append(subcategory).append("\n"));
        });

        StringBuilder preferredMerchants = new StringBuilder();
        referenceRepository
                .findByUserExternalUserIdAndUserChannelAndEntityTypeAndActiveTrue(
                        externalUserId, "WHATSAPP", UserReferenceEntityType.MERCHANT)
                .forEach(reference -> {
                    preferredMerchants.append("- ").append(reference.getCanonicalName());
                    var aliases = aliasRepository.findByReferenceEntityId(reference.getId());
                    if (!aliases.isEmpty()) {
                        preferredMerchants.append(" (aliases: ")
                                .append(aliases.stream()
                                        .map(alias -> alias.getAliasText())
                                        .distinct()
                                        .toList())
                                .append(")");
                    }
                    preferredMerchants.append("\n");
                });

        return """
                You normalize personal expense messages into JSON.
                Today's date is %s.

                Return exactly these fields:
                {
                  "amount": number or null,
                  "category": string or null,
                  "subcategory": string or null,
                  "merchant": string or null,
                  "transactionDate": "YYYY-MM-DD" or null,
                  "confidence": number between 0 and 1
                }

                Rules:
                - Extract only facts supported by the user's message.
                - When no date is stated, transactionDate must be today's date.
                - Category and subcategory must be selected only from the taxonomy below.
                - The subcategory must belong to the selected category.
                - Never create a new category or subcategory.
                - If the merchant resembles a preferred merchant or one of its aliases,
                  return its exact canonical name.
                - Return JSON only, without markdown or explanation.

                Configured taxonomy:
                %s

                User's preferred merchants:
                %s
                """.formatted(today, configuredTaxonomy,
                preferredMerchants.isEmpty() ? "- None recorded" : preferredMerchants);
    }

    private java.math.BigDecimal validConfidence(java.math.BigDecimal confidence) {
        if (confidence == null) {
            return null;
        }
        if (confidence.signum() < 0 || confidence.compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new IllegalStateException("Expense normalization confidence must be between 0 and 1");
        }
        return confidence;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
