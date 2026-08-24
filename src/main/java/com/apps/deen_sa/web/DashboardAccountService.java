package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DashboardAccountService {
    private static final Set<String> VISIBLE_TYPES = Set.of(
            "BANK_ACCOUNT", "CASH", "WALLET", "CREDIT_CARD", "PAYABLE", "RECEIVABLE");

    private final StateContainerService containers;
    private final StateChangeRepository transactions;

    public DashboardAccountService(StateContainerService containers, StateChangeRepository transactions) {
        this.containers = containers;
        this.transactions = transactions;
    }

    public List<DashboardAccount> activeAccounts(Long userId, ZoneId timezone) {
        YearMonth currentMonth = YearMonth.now(timezone);
        Instant start = currentMonth.atDay(1).atStartOfDay(timezone).toInstant();
        Instant end = currentMonth.plusMonths(1).atDay(1).atStartOfDay(timezone).toInstant();
        Map<Long, Long> transactionCounts = transactions
                .countActiveTransactionsByAccount(userId.toString(), start, end).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()));

        return containers.getActiveContainers(userId).stream()
                .filter(container -> VISIBLE_TYPES.contains(container.getContainerType()))
                .map(container -> map(container, transactionCounts.getOrDefault(container.getId(), 0L)))
                .sorted(Comparator.comparingLong(DashboardAccount::transactionCount).reversed()
                        .thenComparing(DashboardAccount::lastActivityAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DashboardAccount::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(DashboardAccount::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private DashboardAccount map(StateContainerEntity container, long transactionCount) {
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
                container.getOverLimitAmount(), container.getLastActivityAt(), transactionCount);
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
                                   Instant lastActivityAt, long transactionCount) { }
}
