package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class DashboardAccountService {
    private static final Set<String> VISIBLE_TYPES = Set.of(
            "BANK_ACCOUNT", "CASH", "WALLET", "CREDIT_CARD", "PAYABLE", "RECEIVABLE");

    private final StateContainerService containers;

    public DashboardAccountService(StateContainerService containers) {
        this.containers = containers;
    }

    public List<DashboardAccount> activeAccounts(Long userId) {
        return containers.getActiveContainers(userId).stream()
                .filter(container -> VISIBLE_TYPES.contains(container.getContainerType()))
                .map(this::map)
                .toList();
    }

    private DashboardAccount map(StateContainerEntity container) {
        String type = container.getContainerType();
        BigDecimal current = container.getCurrentValue();
        BigDecimal limit = container.getCapacityLimit();
        BigDecimal availableCredit = "CREDIT_CARD".equals(type) && limit != null
                ? limit.subtract(zero(current)).max(BigDecimal.ZERO) : null;
        BigDecimal cashAvailable = Set.of("BANK_ACCOUNT", "CASH", "WALLET").contains(type)
                ? first(container.getAvailableValue(), current) : null;

        String primaryLabel;
        BigDecimal primaryValue;
        switch (type) {
            case "CREDIT_CARD" -> { primaryLabel = "Available credit"; primaryValue = availableCredit; }
            case "PAYABLE" -> { primaryLabel = "Outstanding liability"; primaryValue = current; }
            case "RECEIVABLE" -> { primaryLabel = "Amount receivable"; primaryValue = current; }
            default -> { primaryLabel = "Available balance"; primaryValue = cashAvailable; }
        }

        return new DashboardAccount(container.getId(), container.getName(), type,
                container.getCurrency(), primaryLabel, primaryValue,
                Set.of("BANK_ACCOUNT", "CASH", "WALLET").contains(type) ? current : null,
                "CREDIT_CARD".equals(type) ? current : null,
                availableCredit, limit, Boolean.TRUE.equals(container.getOverLimit()),
                container.getOverLimitAmount(), container.getLastActivityAt());
    }

    private BigDecimal first(BigDecimal preferred, BigDecimal fallback) {
        return preferred == null ? fallback : preferred;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record DashboardAccount(Long id, String name, String type, String currency,
                                   String primaryLabel, BigDecimal primaryValue,
                                   BigDecimal currentBalance, BigDecimal outstanding,
                                   BigDecimal availableCredit, BigDecimal creditLimit,
                                   boolean overLimit, BigDecimal overLimitAmount,
                                   Instant lastActivityAt) { }
}
