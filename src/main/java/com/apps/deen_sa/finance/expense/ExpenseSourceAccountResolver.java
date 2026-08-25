package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.legacy.state.*;
import org.springframework.stereotype.Component;

import java.util.*;

/** Resolves an extracted payment rail/name to one of the user's configured accounts. */
@Component
public class ExpenseSourceAccountResolver {
    private final StateContainerService containers;
    private final StateChangeRepository transactions;

    public ExpenseSourceAccountResolver(StateContainerService containers, StateChangeRepository transactions) {
        this.containers = containers; this.transactions = transactions;
    }

    public StateContainerEntity resolve(ExpenseDto dto, Long userId) {
        if (dto == null || dto.getSourceAccount() == null) return null;
        List<StateContainerEntity> active = containers.getActiveContainers(userId);
        String requested = ExpenseHandler.normalizeSourceType(dto.getSourceAccount());
        String requestedName = normalizeName(dto.getSourceAccount());
        List<StateContainerEntity> named = active.stream()
                .filter(account -> account.getName().equalsIgnoreCase(dto.getSourceAccount())
                        || normalizeName(account.getName()).equals(requestedName)
                        || !requestedName.isBlank() && (normalizeName(account.getName()).contains(requestedName)
                        || requestedName.contains(normalizeName(account.getName()))))
                .toList();
        if (named.size() == 1) return named.getFirst();

        String compact = normalizeName(dto.getSourceAccount()).toUpperCase(Locale.ROOT);
        boolean generic = Set.of("UPI", "BANK", "BANKUPI", "BANKACCOUNT", "CARD", "CREDIT",
                "CREDITCARD", "CASH", "WALLET").contains(compact);
        if (!generic) return null;
        List<StateContainerEntity> byType = active.stream()
                .filter(account -> account.getContainerType().equals(requested)).toList();
        if (byType.size() == 1) return byType.getFirst();
        if (byType.size() > 1) {
            Long recentId = transactions.findMostRecentlyUsedActiveSourceId(userId.toString(), requested).orElse(null);
            return byType.stream().filter(account -> Objects.equals(account.getId(), recentId)).findFirst().orElse(null);
        }
        return null;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:my|the)\\s+", "").replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
