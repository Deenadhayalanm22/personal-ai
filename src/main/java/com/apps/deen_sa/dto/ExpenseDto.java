package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
public class ExpenseDto {

    private BigDecimal amount;            // required
    private String category;
    private String subcategory;
    private String merchant;
    private String paymentMethod;         // maps to payment_method
    private String spentAt;       // maps to spent_at
    private String rawText;               // original spoken text

    // additional extracted fields
    private String channel;               // optional (Bike / Car / None)
    private String notes;                 // optional free text

    // details stored as jsonb
    private Map<String, Object> details;

    // embedding (optional, filled later if you enable vector search)
    private float[] embedding;
}
