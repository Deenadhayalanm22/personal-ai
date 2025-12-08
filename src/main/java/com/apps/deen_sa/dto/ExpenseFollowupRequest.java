package com.apps.deen_sa.dto;

import lombok.Data;

@Data
public class ExpenseFollowupRequest {

    /**
     * The partially extracted DTO from the first LLM call.
     * Frontend must send this object back unchanged.
     */
    private ExpenseDto previous;

    /**
     * The name of the missing field the backend asked for.
     * Examples: "paymentMethod", "merchantName", "spentAt"
     */
    private String missingField;

    /**
     * The user's answer to fill the missing field.
     * Example: "UPI" or "Indian Oil" or "2025-02-08"
     */
    private String userValue;
}
