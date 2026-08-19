package com.apps.deen_sa.finance.presentation;

import java.math.BigDecimal;

public record HierarchyPoint(String category, String subcategory, String merchant, BigDecimal amount) { }
