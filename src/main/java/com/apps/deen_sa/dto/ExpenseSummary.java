package com.apps.deen_sa.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@ToString
public class ExpenseSummary {
    // optional based on request flags
    private BigDecimal totalSpend;

    private Map<String, BigDecimal> spendByCategory;
    private Map<String, BigDecimal> spendBySourceAccount;
}
