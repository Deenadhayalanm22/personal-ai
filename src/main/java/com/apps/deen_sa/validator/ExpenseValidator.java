package com.apps.deen_sa.validator;

import com.apps.deen_sa.dto.ExpenseDto;

import java.util.ArrayList;
import java.util.List;

public class ExpenseValidator {

    public static List<String> findMissingFields(ExpenseDto dto) {
        List<String> missing = new ArrayList<>();

        if (dto.getAmount() == null) missing.add("amount");
        if (dto.getCategory() == null || dto.getCategory().isBlank()) missing.add("category");
        if (dto.getPaymentMethod() == null) missing.add("paymentMethod");
        if (dto.getSpentAt() == null) missing.add("spentAt");

        return missing;
    }
}
