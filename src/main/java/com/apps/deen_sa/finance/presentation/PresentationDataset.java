package com.apps.deen_sa.finance.presentation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PresentationDataset(
        Map<String, BigDecimal> dailySpend,
        Map<String, BigDecimal> currentCategories,
        Map<String, BigDecimal> previousCategories,
        List<HierarchyPoint> hierarchy,
        List<FlowPoint> flows,
        BigDecimal incomeTotal
) {
    public PresentationDataset {
        dailySpend = Map.copyOf(dailySpend); currentCategories = Map.copyOf(currentCategories);
        previousCategories = Map.copyOf(previousCategories); hierarchy = List.copyOf(hierarchy); flows = List.copyOf(flows);
    }
    public static PresentationDataset empty() {
        return new PresentationDataset(Map.of(), Map.of(), Map.of(), List.of(), List.of(), BigDecimal.ZERO);
    }
}
