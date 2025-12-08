package com.apps.deen_sa.dto;

import com.apps.deen_sa.entity.ExpenseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ExpenseResponse {

    private boolean needFollowup;
    private boolean saved;
    private String message;
    private List<String> missingFields;
    private ExpenseDto partial;
    private ExpenseEntity savedEntity;

    public static ExpenseResponse followup(String msg, List<String> missing, ExpenseDto partial) {
        return new ExpenseResponse(true, false, msg, missing, partial, null);
    }

    public static ExpenseResponse invalid(String reason) {
        return new ExpenseResponse(false, false, reason, null, null, null);
    }

    public static ExpenseResponse success(ExpenseEntity entity) {
        return new ExpenseResponse(false, true, "Saved successfully", null, null, entity);
    }
}
