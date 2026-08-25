package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DashboardEnrichmentService {
    private static final int DASHBOARD_LIMIT = 20;
    private final StateChangeRepository transactions;
    private final StateContainerRepository accounts;
    private final ExpenseCorrectionService corrections;

    public DashboardEnrichmentService(StateChangeRepository transactions, StateContainerRepository accounts,
                                      ExpenseCorrectionService corrections) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.corrections = corrections;
    }

    public EnrichmentQueue queue(Long userId) {
        List<EnrichmentItem> transactionItems = transactions
                .findNeedsEnrichment(userId.toString(), PageRequest.of(0, DASHBOARD_LIMIT)).stream()
                .map(this::transaction).toList();
        List<EnrichmentItem> accountItems = accounts.findActiveByOwnerId(userId).stream()
                .map(this::account).filter(item -> !item.missingFields().isEmpty()).toList();
        List<EnrichmentItem> combined = new ArrayList<>(transactionItems);
        combined.addAll(accountItems);
        return new EnrichmentQueue(!combined.isEmpty(), combined.size(), List.copyOf(combined));
    }

    private EnrichmentItem transaction(StateChangeEntity value) {
        List<String> missing = new ArrayList<>();
        if (value.getSourceContainerId() == null) missing.add("sourceAccount");
        if (value.getCategory() == null || value.getCategory().isBlank()) missing.add("category");
        if (value.getSubcategory() == null || value.getSubcategory().isBlank()) missing.add("subcategory");
        if (!value.isFinanciallyApplied()) missing.add("accountBalanceImpact");
        return new EnrichmentItem("TRANSACTION", value.getId(), "⚠ Transaction needs details",
                value.getRawText(), missing, "/portal/expenses/" + value.getId(), value.getRecordVersion());
    }

    private EnrichmentItem account(StateContainerEntity value) {
        List<String> missing = new ArrayList<>();
        if (value.getCurrentValue() == null) missing.add("currentBalance");
        if ("CREDIT_CARD".equals(value.getContainerType())) {
            if (value.getCapacityLimit() == null) missing.add("creditLimit");
            Map<String, Object> details = value.getDetails();
            if (details == null || details.get("billingDay") == null) missing.add("billingDay");
            if (details == null || details.get("dueDay") == null) missing.add("dueDay");
        }
        return new EnrichmentItem("ACCOUNT", value.getId(), "⚠ Account needs details",
                value.getName(), missing, "/portal/accounts/" + value.getId(), null);
    }

    public void discardTransaction(Long userId, Long transactionId, int version) {
        if (version < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid record version is required");
        try {
            corrections.voidEnrichmentExpense(userId, transactionId, version);
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Enrichment transaction not found or no longer available");
        } catch (IllegalStateException unsafe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unsafe.getMessage());
        }
    }

    public record EnrichmentQueue(boolean hasItems, int count, List<EnrichmentItem> items) { }
    public record EnrichmentItem(String type, Long id, String alertLabel, String description,
                                 List<String> missingFields, String portalPath, Integer version) { }
}
