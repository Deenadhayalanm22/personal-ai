package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import com.apps.deen_sa.finance.tag.*;
import com.apps.deen_sa.finance.legacy.state.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final TagRepository tags;
    private final TransactionTagRepository transactionTags;

    public WebExpenseService(StateChangeRepository expenses, StateContainerRepository accounts,
                             ExpenseCorrectionService corrections, WebExpenseTaxonomyService taxonomy,
                             TagRepository tags, TransactionTagRepository transactionTags) {
        this.expenses = expenses; this.accounts = accounts; this.corrections = corrections;
        this.taxonomy = taxonomy;
        this.tags = tags; this.transactionTags = transactionTags;
    }

    public ExpensePage list(AppUserEntity user, YearMonth month, int requestedLimit, Long beforeId,
                            ExpenseFilter requestedFilter) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        ExpenseFilter filter = normalize(requestedFilter);
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant start = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        String userId = String.valueOf(user.getId());
        boolean categoryFiltered = filter.category() != null;
        boolean subcategoryFiltered = filter.subcategory() != null;
        String categoryQuery = categoryFiltered ? filter.category() : "";
        String subcategoryQuery = subcategoryFiltered ? filter.subcategory() : "";
        boolean tagFiltered = !filter.tagIds().isEmpty();
        if (tagFiltered) ownedTags(user.getId(), filter.tagIds());
        List<StateChangeEntity> found = tagFiltered
                ? expenses.findTagFilteredActiveExpensesBefore(
                        userId, start, end, filter.accountId(), categoryFiltered, categoryQuery,
                        subcategoryFiltered, subcategoryQuery, filter.tagIds(),
                        filter.tagMatch() == TagMatch.ALL, filter.tagIds().size(), beforeId,
                        PageRequest.of(0, limit + 1))
                : expenses.findFilteredActiveExpensesBefore(
                        userId, start, end, filter.accountId(), categoryFiltered, categoryQuery,
                        subcategoryFiltered, subcategoryQuery, beforeId, PageRequest.of(0, limit + 1));
        boolean hasMore = found.size() > limit;
        List<StateChangeEntity> visible = hasMore ? found.subList(0, limit) : found;
        Map<Long, String> accountNames = accountNames(visible);
        Map<Long, List<TagItem>> tagItems = tagItems(visible);
        List<ExpenseItem> items = visible.stream()
                .map(row -> item(row, user.getCurrency(), accountNames, tagItems)).toList();
        Long next = hasMore && !visible.isEmpty() ? visible.getLast().getId() : null;
        List<Object[]> summaryRows = tagFiltered
                ? expenses.summarizeTagFilteredActiveExpenses(
                        userId, start, end, filter.accountId(), categoryFiltered, categoryQuery,
                        subcategoryFiltered, subcategoryQuery, filter.tagIds(),
                        filter.tagMatch() == TagMatch.ALL, filter.tagIds().size())
                : expenses.summarizeFilteredActiveExpenses(
                        userId, start, end, filter.accountId(), categoryFiltered, categoryQuery,
                        subcategoryFiltered, subcategoryQuery);
        Object[] summary = summaryRows.isEmpty() ? new Object[]{0L, BigDecimal.ZERO} : summaryRows.getFirst();
        FilterSummary filterSummary = new FilterSummary(((Number) summary[0]).longValue(),
                (BigDecimal) summary[1], user.getCurrency(), filter.accountId(), filter.category(), filter.subcategory(),
                filter.tagIds(), filter.tagMatch().name().toLowerCase(Locale.ROOT));
        return new ExpensePage(items, next, filterSummary);
    }

    @Transactional
    public ExpenseItem editClassification(AppUserEntity user, Long id, ClassificationUpdate request) {
        if (request == null || request.version() < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid record version is required");
        boolean editsClassification = request.category() != null || request.subcategory() != null;
        if (!editsClassification && request.tagIds() == null && request.sourceAccountId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide a classification, source account, or tag IDs to update");
        WebExpenseTaxonomyService.Classification classification = null;
        if (editsClassification) {
            String category = clean(request.category(), "category");
            String subcategory = clean(request.subcategory(), "subcategory");
            classification = taxonomy.validate(category, subcategory);
        }
        List<TagEntity> selectedTags = request.tagIds() == null ? null : ownedTags(user.getId(), request.tagIds());
        try {
            StateChangeEntity updated = corrections.editDetails(
                    user.getId(), id, request.version(),
                    classification == null ? null : classification.category(),
                    classification == null ? null : classification.subcategory(), request.sourceAccountId());
            applyTags(id, updated.getId(), selectedTags);
            return item(updated, user.getCurrency(), accountNames(List.of(updated)), tagItems(List.of(updated)));
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
        return item(row, currency, accountNames, Map.of());
    }

    private ExpenseItem item(StateChangeEntity row, String currency, Map<Long, String> accountNames,
                             Map<Long, List<TagItem>> tagsByTransaction) {
        return new ExpenseItem(row.getId(), row.getRawText(), row.getAmount(), currency, row.getTimestamp(),
                row.getCategory(), row.getSubcategory(), row.getMainEntity(),
                row.getSourceContainerId() == null ? null : accountNames.get(row.getSourceContainerId()),
                row.isNeedsEnrichment(), row.getRecordVersion(),
                tagsByTransaction.getOrDefault(row.getId(), List.of()));
    }

    private List<TagEntity> ownedTags(Long userId, List<Long> requestedIds) {
        if (requestedIds.stream().anyMatch(Objects::isNull))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag IDs cannot contain null");
        Set<Long> uniqueIds = new LinkedHashSet<>(requestedIds);
        List<TagEntity> found = uniqueIds.isEmpty() ? List.of() : tags.findAllByUserIdAndIdIn(userId, uniqueIds);
        if (found.size() != uniqueIds.size())
            throw new WebApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_TAG",
                    "One or more selected tags are unavailable");
        return found;
    }

    private void applyTags(Long originalId, Long replacementId, List<TagEntity> selectedTags) {
        Collection<Long> ids = selectedTags == null
                ? transactionTags.findAllByTransactionId(originalId).stream().map(TransactionTagEntity::getTagId).toList()
                : selectedTags.stream().map(TagEntity::getId).toList();
        Instant now = Instant.now();
        transactionTags.saveAll(ids.stream().map(tagId -> {
            TransactionTagEntity link = new TransactionTagEntity();
            link.setTransactionId(replacementId); link.setTagId(tagId); link.setCreatedAt(now);
            return link;
        }).toList());
    }

    private Map<Long, List<TagItem>> tagItems(List<StateChangeEntity> rows) {
        Set<Long> transactionIds = rows.stream().map(StateChangeEntity::getId).collect(java.util.stream.Collectors.toSet());
        if (transactionIds.isEmpty()) return Map.of();
        List<TransactionTagEntity> links = transactionTags.findAllByTransactionIdIn(transactionIds);
        Set<Long> tagIds = links.stream().map(TransactionTagEntity::getTagId).collect(java.util.stream.Collectors.toSet());
        Map<Long, TagEntity> tagById = tags.findAllById(tagIds).stream()
                .collect(java.util.stream.Collectors.toMap(TagEntity::getId, value -> value));
        Map<Long, List<TagItem>> result = new HashMap<>();
        for (TransactionTagEntity link : links) {
            TagEntity tag = tagById.get(link.getTagId());
            if (tag != null) result.computeIfAbsent(link.getTransactionId(), ignored -> new ArrayList<>())
                    .add(new TagItem(tag.getId(), tag.getName()));
        }
        result.values().forEach(values -> values.sort(Comparator.comparing(TagItem::name, String.CASE_INSENSITIVE_ORDER)));
        return result;
    }

    private String clean(String value, String field) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " cannot be blank");
        if (cleaned.length() > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is too long");
        return cleaned;
    }

    private ExpenseFilter normalize(ExpenseFilter filter) {
        if (filter == null) return new ExpenseFilter(null, null, null, List.of(), TagMatch.ANY);
        List<Long> tagIds = filter.tagIds() == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(filter.tagIds()));
        if (tagIds.stream().anyMatch(value -> value == null || value < 1))
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_TAG_IDS",
                    "tagIds must contain positive integer IDs");
        return new ExpenseFilter(filter.accountId(), clean(filter.category(), "category"),
                clean(filter.subcategory(), "subcategory"), List.copyOf(tagIds),
                filter.tagMatch() == null ? TagMatch.ANY : filter.tagMatch());
    }

    public record ClassificationUpdate(String category, String subcategory, int version, List<Long> tagIds,
                                       Long sourceAccountId) {
        public ClassificationUpdate(String category, String subcategory, int version, List<Long> tagIds) {
            this(category, subcategory, version, tagIds, null);
        }
    }
    public record ExpenseFilter(Long accountId, String category, String subcategory,
                                List<Long> tagIds, TagMatch tagMatch) {
        public ExpenseFilter(Long accountId, String category, String subcategory) {
            this(accountId, category, subcategory, List.of(), TagMatch.ANY);
        }
    }
    public enum TagMatch { ANY, ALL }
    public record FilterSummary(long transactionCount, BigDecimal totalAmount, String currency,
                                Long accountId, String category, String subcategory,
                                List<Long> tagIds, String tagMatch) {
        public FilterSummary(long transactionCount, BigDecimal totalAmount, String currency,
                             Long accountId, String category, String subcategory) {
            this(transactionCount, totalAmount, currency, accountId, category, subcategory, List.of(), "any");
        }
    }
    public record ExpensePage(List<ExpenseItem> items, Long nextBeforeId, FilterSummary filterSummary) { }
    public record ExpenseItem(Long id, String originalMessage, BigDecimal amount, String currency,
                              Instant transactionTime, String category, String subcategory, String merchant,
                              String sourceAccount, boolean needsReview, int version, List<TagItem> tags) { }
    public record TagItem(Long id, String name) { }
}
