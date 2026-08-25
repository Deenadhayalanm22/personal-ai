package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import com.apps.deen_sa.finance.expense.ExpenseSourceAccountResolver;
import com.apps.deen_sa.finance.expense.draft.*;
import com.apps.deen_sa.dto.ExpenseDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class DashboardEnrichmentService {
    private static final int DASHBOARD_LIMIT = 20;
    private final StateChangeRepository transactions;
    private final StateContainerRepository accounts;
    private final ExpenseCorrectionService corrections;
    private final ExpenseDraftService drafts;
    private final ExpenseHandler expenseHandler;
    private final ExpenseSourceAccountResolver sourceAccounts;

    public DashboardEnrichmentService(StateChangeRepository transactions, StateContainerRepository accounts,
                                      ExpenseCorrectionService corrections, ExpenseDraftService drafts,
                                      ExpenseHandler expenseHandler, ExpenseSourceAccountResolver sourceAccounts) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.corrections = corrections;
        this.drafts = drafts; this.expenseHandler = expenseHandler; this.sourceAccounts = sourceAccounts;
    }

    public EnrichmentQueue queue(Long userId) {
        List<EnrichmentItem> transactionItems = transactions
                .findNeedsEnrichment(userId.toString(), PageRequest.of(0, DASHBOARD_LIMIT)).stream()
                .map(this::transaction).toList();
        List<EnrichmentItem> accountItems = accounts.findActiveByOwnerId(userId).stream()
                .map(this::account).filter(item -> !item.missingFields().isEmpty()).toList();
        List<EnrichmentItem> combined = new ArrayList<>(transactionItems);
        combined.addAll(drafts.pending(userId, DASHBOARD_LIMIT).stream().map(this::draft).toList());
        combined.addAll(accountItems);
        return new EnrichmentQueue(!combined.isEmpty(), combined.size(), List.copyOf(combined));
    }

    private EnrichmentItem transaction(StateChangeEntity value) {
        List<String> missing = new ArrayList<>();
        if (value.getSourceContainerId() == null) missing.add("sourceAccount");
        if (value.getCategory() == null || value.getCategory().isBlank()) missing.add("category");
        if (value.getSubcategory() == null || value.getSubcategory().isBlank()) missing.add("subcategory");
        if (!value.isFinanciallyApplied()) missing.add("accountBalanceImpact");
        String accountName = value.getSourceContainerId() == null ? null : accounts.findById(value.getSourceContainerId())
                .filter(account -> account.getOwnerId().toString().equals(value.getUserId()))
                .map(StateContainerEntity::getName).orElse(null);
        return new EnrichmentItem("TRANSACTION", value.getId(), "⚠ Transaction needs details",
                value.getRawText(), missing, "/portal/expenses/" + value.getId(), value.getRecordVersion(),
                null, value.getAmount(), value.getCategory(), value.getSubcategory(), value.getMainEntity(),
                null, value.getTimestamp() == null ? null : value.getTimestamp().atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                value.getSourceContainerId(), accountName);
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
                value.getName(), missing, "/portal/accounts/" + value.getId(), null,
                null, null, null, null, null, null, null, null, null);
    }

    private EnrichmentItem draft(ExpenseDraftEntity value) {
        ExpenseDto dto = drafts.toDto(value);
        StateContainerEntity resolved = sourceAccounts.resolve(dto, value.getUserId());
        return new EnrichmentItem("EXPENSE_DRAFT", value.getId(), "Transaction waiting for your response",
                value.getRawText(), value.getMissingFields(), "/portal/enrichment/drafts/" + value.getId(),
                value.getVersion(), value.getSourceChannel(), dto.getAmount(), dto.getCategory(), dto.getSubcategory(),
                dto.getMerchantName(), dto.getSourceAccount(), dto.getTransactionDate(),
                resolved == null ? null : resolved.getId(), resolved == null ? null : resolved.getName());
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

    public EnrichmentItem updateDraft(Long userId, Long id, DraftUpdate request) {
        if (request == null || request.version() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid draft version is required");
        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        if (request.category() != null) patch.put("category", request.category());
        if (request.subcategory() != null) patch.put("subcategory", request.subcategory());
        if (request.sourceAccount() != null) patch.put("sourceAccount", request.sourceAccount());
        if (request.merchantName() != null) patch.put("merchantName", request.merchantName());
        if (request.transactionDate() != null) patch.put("transactionDate", request.transactionDate());
        try {
            return draft(drafts.update(userId, id, request.version(), patch));
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (java.util.NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found or no longer available");
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException unavailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unavailable.getMessage());
        }
    }

    public ConfirmedDraft confirmDraft(Long userId, Long id, int version) {
        try {
            StateChangeEntity saved = expenseHandler.confirmPortalDraft(userId, id, version);
            return new ConfirmedDraft(id, saved.getId(), saved.getRecordVersion(), saved.getAmount(),
                    saved.getCategory(), saved.getSubcategory());
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (java.util.NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found or no longer available");
        } catch (IllegalStateException incomplete) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, incomplete.getMessage());
        }
    }

    public void discardDraft(Long userId, Long id, int version) {
        try {
            drafts.discard(userId, id, version);
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (java.util.NoSuchElementException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found or no longer available");
        } catch (IllegalStateException unavailable) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unavailable.getMessage());
        }
    }

    public record EnrichmentQueue(boolean hasItems, int count, List<EnrichmentItem> items) { }
    public record EnrichmentItem(String type, Long id, String alertLabel, String description,
                                 List<String> missingFields, String portalPath, Integer version,
                                 String sourceChannel, BigDecimal amount, String category, String subcategory,
                                 String merchantName, String sourceAccount, LocalDate transactionDate,
                                 Long resolvedSourceAccountId, String resolvedSourceAccountName) { }
    public record DraftUpdate(int version, String category, String subcategory, String sourceAccount,
                              String merchantName, LocalDate transactionDate) { }
    public record ConfirmedDraft(Long draftId, Long transactionId, int transactionVersion, BigDecimal amount,
                                 String category, String subcategory) { }
}
