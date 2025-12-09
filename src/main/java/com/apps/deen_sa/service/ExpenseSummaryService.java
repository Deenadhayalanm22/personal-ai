package com.apps.deen_sa.service;

import com.apps.deen_sa.dto.ExpenseItemDto;
import com.apps.deen_sa.dto.ExpenseSummaryDto;
import com.apps.deen_sa.repo.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseSummaryService {

    private final ExpenseRepository repo;

    public ExpenseSummaryService(ExpenseRepository repo) {
        this.repo = repo;
    }

    public ExpenseSummaryDto getDashboardSummary() {

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        BigDecimal todayTotal =
                repo.sumAmountByDate(today).orElse(BigDecimal.ZERO);

        BigDecimal monthTotal =
                repo.sumAmountBetweenDates(monthStart, today)
                        .orElse(BigDecimal.ZERO);

        Map<String, BigDecimal> categoryTotals =
                repo.sumAmountGroupByCategory(monthStart, today);

        List<ExpenseItemDto> recent =
                repo.findTop10ByOrderBySpentAtDesc().stream()
                        .map(e -> ExpenseItemDto.builder()
                                .id(e.getId())
                                .amount(e.getAmount())
                                .category(e.getCategory())
                                .subcategory(e.getSubcategory())
                                .merchantName(e.getMerchantName())
                                .spentAt(e.getSpentAt().toLocalDate().toString())
                                .build()
                        )
                        .toList();

        return ExpenseSummaryDto.builder()
                .todayTotal(todayTotal)
                .monthTotal(monthTotal)
                .categoryTotals(categoryTotals)
                .recentTransactions(recent)
                .build();
    }
}
