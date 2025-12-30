package com.apps.deen_sa.dto;

import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class ExpenseDto {

    // ----------------------------
    // VALIDATION STATUS
    // ----------------------------
    private boolean valid;
    private String reason; // populated only if valid = false

    // ----------------------------
    // CORE FINANCIAL DATA
    // ----------------------------
    private BigDecimal amount;

    // Expense-specific classification
    private String category;        // Food & Dining, Transportation, etc.
    private String subcategory;     // Fuel, Groceries, Eating Out, etc.

    // Who was paid
    private String merchantName;    // Amazon, Swiggy, Indian Oil, etc.

    // From where it was paid
    private String sourceAccount;   // Credit Card, Cash

    // When it happened
    private LocalDate transactionDate; // renamed from spentAt

    // ----------------------------
    // CONTEXT & SEMANTICS
    // ----------------------------
    private List<String> tags;      // fuel, grocery, commute, celebration, etc.

    // Flexible extra info
    private Map<String, Object> details;
    /*
        Examples:
        {
          "vehicleType": "car",
          "platform": "Swiggy",
          "cardLast4": "1234",
          "fuelLitres": 12.5
        }
    */

    // ----------------------------
    // RAW INPUT (VERY IMPORTANT)
    // ----------------------------
    private String rawText;

    // ----------------------------
    // BACKEND-ONLY HELPERS
    // ----------------------------
    private List<String> missingFields;

    private boolean sourceResolved;

    private String paymentMethod;

    private String spentAt;

    private CompletenessLevelEnum completenessLevelEnum;
}
