package com.apps.deen_sa.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExpenseSummaryDto {

    private BigDecimal todayTotal;
    private BigDecimal monthTotal;

    private Map<String, BigDecimal> categoryTotals;  // e.g., Food: 1200, Transport: 450

    private List<ExpenseItemDto> recentTransactions; // last 5–10 items
}
