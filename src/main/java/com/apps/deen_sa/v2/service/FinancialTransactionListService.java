package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.entity.FinancialTransactionEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import com.apps.deen_sa.web.WebApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialTransactionListService {
    private static final int MAX_PAGE_SIZE = 100;

    private final FinancialTransactionRepository transactions;

    @Transactional(readOnly = true)
    public ExpensePage list(
            AppUserEntity user,
            YearMonth month,
            int requestedLimit,
            Long beforeId,
            ExpenseFilter filter
    ) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        ExpenseFilter normalized = normalize(filter);
        if (normalized.date() != null && !YearMonth.from(normalized.date()).equals(month)) {
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER_DATE",
                    "date must be within the selected month");
        }

        LocalDate start = normalized.date() == null ? month.atDay(1) : normalized.date();
        LocalDate end = normalized.date() == null
                ? month.plusMonths(1).atDay(1)
                : normalized.date().plusDays(1);
        List<FinancialTransactionEntity> found = transactions.findVisibleBefore(
                user.getId(), start, end, normalized.category(), normalized.subcategory(),
                beforeId, PageRequest.of(0, limit + 1));
        boolean hasMore = found.size() > limit;
        List<FinancialTransactionEntity> visible = hasMore ? found.subList(0, limit) : found;
        ZoneId userZone = ZoneId.of(user.getTimezone());
        List<ExpenseItem> items = visible.stream()
                .map(row -> item(row, user.getCurrency(), userZone))
                .toList();
        Long nextBeforeId = hasMore && !visible.isEmpty() ? visible.getLast().getId() : null;

        Object[] summary = transactions.summarizeVisible(
                        user.getId(), start, end,
                        normalized.category(), normalized.subcategory())
                .stream().findFirst()
                .orElse(new Object[]{0L, new BigDecimal("0.00")});
        FilterSummary filterSummary = new FilterSummary(
                ((Number) summary[0]).longValue(), money((BigDecimal) summary[1]),
                user.getCurrency(), normalized.category(), normalized.subcategory(),
                List.of(), "any");
        return new ExpensePage(items, nextBeforeId, filterSummary);
    }

    private ExpenseItem item(FinancialTransactionEntity row, String currency, ZoneId zone) {
        String merchant = row.getMerchant() == null ? null : row.getMerchant().getCanonicalName();
        Instant transactionTime = row.getOccurredAt().atStartOfDay(zone).toInstant();
        return new ExpenseItem(
                row.getId(), row.getSourceDraft().getRawText(), money(row.getAmount()), currency,
                transactionTime, row.getCategory(), row.getSubcategory(), merchant,
                null, false, 1, List.of());
    }

    private ExpenseFilter normalize(ExpenseFilter filter) {
        if (filter == null) {
            return new ExpenseFilter(null, null, null);
        }
        return new ExpenseFilter(clean(filter.category()), clean(filter.subcategory()), filter.date());
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record ExpenseFilter(String category, String subcategory, LocalDate date) {
    }

    public record FilterSummary(
            long transactionCount,
            BigDecimal totalAmount,
            String currency,
            String category,
            String subcategory,
            List<Long> tagIds,
            String tagMatch
    ) {
    }

    public record ExpensePage(
            List<ExpenseItem> items,
            Long nextBeforeId,
            FilterSummary filterSummary
    ) {
    }

    public record ExpenseItem(
            Long id,
            String originalMessage,
            BigDecimal amount,
            String currency,
            Instant transactionTime,
            String category,
            String subcategory,
            String merchant,
            String sourceAccount,
            boolean needsReview,
            int version,
            List<TagItem> tags
    ) {
    }

    public record TagItem(Long id, String name) {
    }
}
