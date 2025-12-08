package com.apps.deen_sa.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class ExpenseDto {

    private boolean valid;
    private String reason;               // Only if valid = false

    private BigDecimal amount;
    private String category;
    private String subcategory;
    private String merchantName;
    private String paymentMethod;

    private LocalDate spentAt;           // YYYY-MM-DD from LLM

    private String rawText;

    private Map<String, Object> details;
    private List<String> tags;

    // List of missing fields
    private List<String> missingFields;
}
