package com.apps.deen_sa.finance.budget;

import java.math.BigDecimal;

public record BudgetProgress(String category, BigDecimal spent, BigDecimal limit) { }
