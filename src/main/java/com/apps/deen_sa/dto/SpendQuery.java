package com.apps.deen_sa.dto;

public record SpendQuery(
        String dimensionType,   // MERCHANT | CATEGORY | SUBCATEGORY | TAG
        String dimensionValue,  // amazon | Shopping | Online Purchase | child
        TimeRange range         // resolved time window
) {}
