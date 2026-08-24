package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import com.apps.deen_sa.finance.legacy.state.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class WebExpenseService {
    private static final int MAX_PAGE_SIZE = 100;
    private final StateChangeRepository expenses;
    private final StateContainerRepository accounts;
    private final ExpenseCorrectionService corrections;
    private final WebExpenseTaxonomyService taxonomy;

    public WebExpenseService(StateChangeRepository expenses, StateContainerRepository accounts,
                             ExpenseCorrectionService corrections, WebExpenseTaxonomyService taxonomy) {
        this.expenses = expenses; this.accounts = accounts; this.corrections = corrections;
        this.taxonomy = taxonomy;
    }

    public ExpensePage list(AppUserEntity user, YearMonth month, int requestedLimit, Long beforeId,
                            ExpenseFilter requestedFilter) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        ExpenseFilter filter = normalize(requestedFilter);
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        String userId = String.valueOf(user.getId());
        List<StateChangeEntity> found = expenses.findFilteredActiveExpensesBefore(
                userId, start, end, filter.accountId(), filter.category(), filter.subcategory(), beforeId,
                PageRequest.of(0, limit + 1));
        boolean hasMore = found.size() > limit;
        List<StateChangeEntity> visible = hasMore ? found.subList(0, limit) : found;
        Map<Long, String> accountNames = accountNames(visible);
        List<ExpenseItem> items = visible.stream().map(row -> item(row, user.getCurrency(), accountNames)).toList();
        Long next = hasMore && !visible.isEmpty() ? visible.getLast().getId() : null;
        List<Object[]> summaryRows = expenses.summarizeFilteredActiveExpenses(
                userId, start, end, filter.accountId(), filter.category(), filter.subcategory());
        Object[] summary = summaryRows.isEmpty() ? new Object[]{0L, BigDecimal.ZERO} : summaryRows.getFirst();
        FilterSummary filterSummary = new FilterSummary(((Number) summary[0]).longValue(),
                (BigDecimal) summary[1], user.getCurrency(), filter.accountId(), filter.category(), filter.subcategory());
        return new ExpensePage(items, next, filterSummary);
    }

    public ExpenseItem editClassification(AppUserEntity user, Long id, ClassificationUpdate request) {
        if (request == null || request.version() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid record version is required");
        String category = clean(request.category(), "category");
        String subcategory = clean(request.subcategory(), "subcategory");
        WebExpenseTaxonomyService.Classification classification = taxonomy.validate(category, subcategory);
        try {
            StateChangeEntity updated = corrections.editClassification(
                    user.getId(), id, request.version(), classification.category(), classification.subcategory());
            return item(updated, user.getCurrency(), accountNames(List.of(updated)));
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found or no longer active");
        } catch (IllegalStateException unsafe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unsafe.getMessage());
        }
    }

    public void delete(AppUserEntity user, Long id, int version) {
        if (version < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid record version is required");
        try {
            corrections.voidExpense(user.getId(), id, version);
        } catch (org.springframework.dao.OptimisticLockingFailureException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found or no longer active");
        } catch (IllegalStateException unsafe) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, unsafe.getMessage());
        }
    }

    private Map<Long, String> accountNames(List<StateChangeEntity> rows) {
        Set<Long> ids = new HashSet<>();
        rows.stream().map(StateChangeEntity::getSourceContainerId).filter(Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> names = new HashMap<>();
        accounts.findAllById(ids).forEach(account -> names.put(account.getId(), account.getName()));
        return names;
    }

    private ExpenseItem item(StateChangeEntity row, String currency, Map<Long, String> accountNames) {
        return new ExpenseItem(row.getId(), row.getRawText(), row.getAmount(), currency, row.getTimestamp(),
                row.getCategory(), row.getSubcategory(), row.getMainEntity(),
                row.getSourceContainerId() == null ? null : accountNames.get(row.getSourceContainerId()),
                row.isNeedsEnrichment(), row.getRecordVersion());
    }

    private String clean(String value, String field) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " cannot be blank");
        if (cleaned.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
        return cleaned;
    }

    private ExpenseFilter normalize(ExpenseFilter filter) {
        if (filter == null) return new ExpenseFilter(null, null, null);
        return new ExpenseFilter(filter.accountId(), clean(filter.category(), "category"),
                clean(filter.subcategory(), "subcategory"));
    }

    public record ClassificationUpdate(String category, String subcategory, int version) { }
    public record ExpenseFilter(Long accountId, String category, String subcategory) { }
    public record FilterSummary(long transactionCount, BigDecimal totalAmount, String currency,
                                Long accountId, String category, String subcategory) { }
    public record ExpensePage(List<ExpenseItem> items, Long nextBeforeId, FilterSummary filterSummary) { }
    public record ExpenseItem(Long id, String originalMessage, BigDecimal amount, String currency,
                              Instant transactionTime, String category, String subcategory, String merchant,
                              String sourceAccount, boolean needsReview, int version) { }
}
