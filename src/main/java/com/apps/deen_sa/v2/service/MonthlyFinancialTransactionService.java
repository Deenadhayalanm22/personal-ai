package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.v2.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MonthlyFinancialTransactionService {
    private final FinancialTransactionRepository transactions;

    @Transactional(readOnly = true)
    public MonthlyExpenseResponse summarize(AppUserEntity user, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        transactions.sumByCategoryForPeriod(user.getId(), start, end)
                .forEach(row -> categories.put((String) row[0], (BigDecimal) row[1]));

        BigDecimal total = categories.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long transactionCount = transactions
                .countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanAndDeletedAtIsNull(
                        user.getId(), start, end);

        return new MonthlyExpenseResponse(
                month.toString(), user.getCurrency(), total, transactionCount, categories);
    }

    public record MonthlyExpenseResponse(
            String month,
            String currency,
            BigDecimal total,
            long transactionCount,
            Map<String, BigDecimal> categories
    ) {
    }
}
