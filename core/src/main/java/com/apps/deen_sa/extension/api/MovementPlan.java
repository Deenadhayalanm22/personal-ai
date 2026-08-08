package com.apps.deen_sa.extension.api;

import java.math.BigDecimal;

public record MovementPlan(String resourceId, String containerId, BigDecimal quantity, String unitId) {
    public MovementPlan {
        if (resourceId == null || resourceId.isBlank()) throw new IllegalArgumentException("resourceId is required");
        if (containerId == null || containerId.isBlank()) throw new IllegalArgumentException("containerId is required");
        if (quantity == null || quantity.signum() == 0) throw new IllegalArgumentException("quantity must be signed and non-zero");
        if (unitId == null || unitId.isBlank()) throw new IllegalArgumentException("unitId is required");
    }
}
