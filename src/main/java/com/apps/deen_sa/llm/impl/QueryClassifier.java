package com.apps.deen_sa.llm.impl;

import com.apps.deen_sa.Registry.ExpenseTaxonomyRegistry;
import com.apps.deen_sa.dto.QueryResult;
import com.apps.deen_sa.llm.BaseLLMExtractor;
import com.openai.client.OpenAIClient;
import org.springframework.stereotype.Service;

@Service
public class QueryClassifier extends BaseLLMExtractor {

    private final ExpenseTaxonomyRegistry expenseTaxonomyRegistry;

    private static final String PROMPT = """
            You are a financial query interpreter.
            
            Your task is to understand EXPENSE-RELATED QUESTIONS
            and configure an expense analytics request.
            
            ------------------------------------------------
            KNOWN CATEGORIES & SUBCATEGORIES
            ------------------------------------------------
            %s
            
            ------------------------------------------------
            FILTER EXTRACTION (STRICT)
            ------------------------------------------------
            
            Extract ONLY if explicitly mentioned, else null:
            
            - category        → Housing, Food & Dining, Shopping, etc.
            - subcategory     → Rent, Groceries, Online Purchase, etc.
            - merchant        → Amazon, Swiggy, Zomato, FirstCry
            - tag             → child, delivery, subscription
            
            ------------------------------------------------
            SOURCE ACCOUNT (MANDATORY IF PAYMENT MENTIONED)
            ------------------------------------------------
            
            sourceAccount → WHERE the money came from
            
            Allowed values:
            - BANK_ACCOUNT
            - CREDIT_CARD
            - WALLET
            - CASH
            
            Rules (NO EXCEPTIONS):
            - UPI, GPay, PhonePe, NetBanking, Bank Transfer → sourceAccount = BANK_ACCOUNT
            - Credit card, card EMI → sourceAccount = CREDIT_CARD
            - Cash → sourceAccount = CASH
            - Never invent values
            - If payment is mentioned, sourceAccount MUST be set
            
            ------------------------------------------------
            AGGREGATION RULES
            ------------------------------------------------
            
            If user asks for:
            - "summary", "overview", "breakdown":
              queryType = EXPENSE_SUMMARY
              includeTotal = true
              groupByCategory = true
              groupBySourceAccount = true
            
            If user asks:
            - "how much did I spend":
              queryType = EXPENSE_TOTAL
              includeTotal = true
            
            ------------------------------------------------
            TIME PERIOD
            ------------------------------------------------
            TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, LAST_MONTH, LAST_YEAR
            
            ------------------------------------------------
            OUTPUT FORMAT (STRICT JSON ONLY)
            ------------------------------------------------
            
            {
              "intent": "QUERY",
              "queryType": "EXPENSE_SUMMARY | EXPENSE_TOTAL",
              "timePeriod": string | null,
            
              "category": string | null,
              "subcategory": string | null,
              "merchant": string | null,
              "sourceAccount": string | null,
              "tag": string | null,
            
              "includeTotal": boolean,
              "groupByCategory": boolean,
              "groupBySourceAccount": boolean,
            
              "confidence": 0.0 to 1.0
            }
            
            ------------------------------------------------
            User question:
            "%s"
            """;

    protected QueryClassifier(
            OpenAIClient client,
            ExpenseTaxonomyRegistry expenseTaxonomyRegistry
    ) {
        super(client);
        this.expenseTaxonomyRegistry = expenseTaxonomyRegistry;
    }

    public QueryResult classify(String text) {
        return callAndParse(
                PROMPT.formatted(
                        renderTaxonomy(expenseTaxonomyRegistry),
                        text
                ),
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
