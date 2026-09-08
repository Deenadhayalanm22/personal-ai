package com.apps.deen_sa.normalization;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.service.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
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
        ExpenseFacts normalized = enforceTaxonomy(extracted, today);
        if (normalized.sourceAccount() != null) {
            return normalized;
        }

        String recoveredSourceAccount = recoverSourceAccount(externalUserId, rawText);
        return new ExpenseFacts(
                normalized.amount(),
                normalized.category(),
                normalized.subcategory(),
                normalized.merchant(),
                recoveredSourceAccount,
                normalized.transactionDate(),
                normalized.confidence());
    }

    private String recoverSourceAccount(String externalUserId, String rawText) {
        SourceAccountFacts recovered = callAndParse(
                """
                Extract only the source account used to pay for an expense.

                Return exactly:
                {"sourceAccount": string or null}

                Rules, in priority order:
                - An explicitly named account in the message wins over every preferred account.
                - Account type is part of identity. A credit card, debit card, and bank account are
                  different accounts even when they share the same institution name.
                - Example: if "HDFC bank account" is preferred but the message says "HDFC credit card",
                  return "HDFC credit card". Never return the bank account and never return null.
                - If an explicit account matches a preferred account of the same type, return the exact
                  preferred canonical name.
                - For a generic reference such as "credit card", return a preferred account only when
                  exactly one preferred account has that type; otherwise return null.
                - UPI, bank transfer, and payment apps are payment methods, not source accounts.
                - Return JSON only.

                Preferred accounts:
                %s
                """.formatted(preferredReferences(externalUserId, UserReferenceEntityType.ACCOUNT)),
                "Expense message:\n" + rawText,
                SourceAccountFacts.class);
        return recovered == null ? null : blankToNull(recovered.sourceAccount());
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
                blankToNull(extracted.sourceAccount()),
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

        String preferredMerchants = preferredReferences(externalUserId, UserReferenceEntityType.MERCHANT);
        String preferredAccounts = preferredReferences(externalUserId, UserReferenceEntityType.ACCOUNT);

        return """
                You normalize personal expense messages into JSON.
                Today's date is %s.

                Return exactly these fields:
                {
                  "amount": number or null,
                  "category": string or null,
                  "subcategory": string or null,
                  "merchant": string or null,
                  "sourceAccount": string or null,
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
                - sourceAccount identifies the user's account or funding source named in the message,
                  such as "HDFC Salary Account", "Amazon ICICI Card", or "Cash".
                - Do not use a payment method such as UPI or bank transfer as sourceAccount.
                - If the source account resembles a preferred account or one of its aliases,
                  return its exact canonical name.
                - Preferred accounts are not a whitelist. If the user explicitly names an account
                  that is not in the preferred accounts, return the account name stated by the user.
                - Account type is part of the account's identity. Never match a named credit card or
                  debit card to a bank, salary, savings, or current account just because the institution
                  name matches. For example, "HDFC credit card" is not "HDFC bank account".
                - Resolve partial account references against the preferred accounts. For example,
                  "HDFC credit card" can refer to a preferred HDFC card.
                - A generic reference such as "credit card" or "debit card" can refer to a preferred
                  account when exactly one preferred account matches that account type. Return that
                  account's exact canonical name. If multiple accounts match and the message does not
                  identify which one, return null rather than guessing.
                - Return JSON only, without markdown or explanation.

                Configured taxonomy:
                %s

                User's preferred merchants:
                %s

                User's preferred accounts:
                %s
                """.formatted(today, configuredTaxonomy,
                preferredMerchants,
                preferredAccounts);
    }

    private String preferredReferences(String externalUserId, UserReferenceEntityType type) {
        StringBuilder references = new StringBuilder();
        referenceRepository
                .findByUserExternalUserIdAndUserChannelAndEntityTypeAndActiveTrue(
                        externalUserId, "WHATSAPP", type)
                .forEach(reference -> {
                    references.append("- ").append(reference.getCanonicalName());
                    var aliases = aliasRepository.findByReferenceEntityId(reference.getId());
                    if (!aliases.isEmpty()) {
                        references.append(" (aliases: ")
                                .append(aliases.stream()
                                        .map(alias -> alias.getAliasText())
                                        .distinct()
                                        .toList())
                                .append(")");
                    }
                    references.append("\n");
                });
        return references.isEmpty() ? "- None recorded" : references.toString();
    }

    private record SourceAccountFacts(String sourceAccount) {
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
