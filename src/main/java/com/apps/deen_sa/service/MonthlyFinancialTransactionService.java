package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.repository.FinancialTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MonthlyFinancialTransactionService {
    private final FinancialTransactionRepository transactions;
    private final MoneyStoriesService moneyStories;

    /** Kept for focused unit tests and non-web callers. Spring uses the two-argument constructor. */
    public MonthlyFinancialTransactionService(FinancialTransactionRepository transactions) {
        this(transactions, null);
    }

    @Autowired
    public MonthlyFinancialTransactionService(FinancialTransactionRepository transactions,
                                              MoneyStoriesService moneyStories) {
        this.transactions = transactions;
        this.moneyStories = moneyStories;
    }

    @Transactional(readOnly = true)
    public MonthlyExpenseResponse summarize(AppUserEntity user, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        transactions.sumByCategoryForPeriod(user.getId(), start, end)
                .forEach(row -> categories.put(
                        (String) row[0], money((BigDecimal) row[1])));

        BigDecimal total = categories.values().stream()
                .reduce(new BigDecimal("0.00"), BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long transactionCount = transactions
                .countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanAndDeletedAtIsNull(
                        user.getId(), start, end);

        return new MonthlyExpenseResponse(month.toString(), user.getCurrency(), total, transactionCount, categories,
                moneyStories == null ? MoneyStoriesService.MoneyStoriesResponse.empty()
                        : moneyStories.monthly(user, month));
    }

    /** Web contract for the Money Chapters shelf and expanded story reader. */
    @Transactional(readOnly = true)
    public MoneyStoriesService.MonthlyStoriesApiResponse monthlyStories(AppUserEntity user, YearMonth month) {
        return moneyStories == null
                ? MoneyStoriesService.MonthlyStoriesApiResponse.empty(month, user)
                : moneyStories.monthlyForWeb(user, month);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record MonthlyExpenseResponse(
            String month,
            String currency,
            BigDecimal total,
            long transactionCount,
            Map<String, BigDecimal> categories,
            MoneyStoriesService.MoneyStoriesResponse moneyStories
    ) {
        public MonthlyExpenseResponse(String month, String currency, BigDecimal total, long transactionCount,
                                      Map<String, BigDecimal> categories) {
            this(month, currency, total, transactionCount, categories, MoneyStoriesService.MoneyStoriesResponse.empty());
        }
    }
}
