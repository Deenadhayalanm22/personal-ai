package com.apps.deen_sa.util;

import com.apps.deen_sa.dto.ExpenseDto;

import java.util.HashMap;
import java.util.Map;

public class ExpenseMerger {
    /**
     * Merge the non-null fields from "update" into "base".
     * This does NOT overwrite existing values unless LLM explicitly returned a non-null replacement.
     */
    public static ExpenseDto merge(ExpenseDto base, ExpenseDto update) {

        if (update == null) {
            return base;
        }

        // ---------- SIMPLE FIELDS ----------
        if (update.getAmount() != null) {
            base.setAmount(update.getAmount());
        }

        if (update.getCategory() != null) {
            base.setCategory(update.getCategory());
        }

        if (update.getSubcategory() != null) {
            base.setSubcategory(update.getSubcategory());
        }

        if (update.getMerchantName() != null) {
            base.setMerchantName(update.getMerchantName());
        }

        if (update.getPaymentMethod() != null) {
            base.setPaymentMethod(update.getPaymentMethod());
        }

        if (update.getSpentAt() != null) {
            base.setSpentAt(update.getSpentAt());
        }

        if (update.getRawText() != null) {
            base.setRawText(update.getRawText());
        }

        // ---------- DETAILS MERGE ----------
        if (update.getDetails() != null && !update.getDetails().isEmpty()) {

            if (base.getDetails() == null) {
                base.setDetails(new HashMap<>());
            }

            // Overwrite or add keys from update
            for (Map.Entry<String, Object> entry : update.getDetails().entrySet()) {
                base.getDetails().put(entry.getKey(), entry.getValue());
            }
        }

        // ---------- TAGS MERGE ----------
        if (update.getTags() != null && !update.getTags().isEmpty()) {
            base.setTags(update.getTags());
        }

        return base;
    }
}
