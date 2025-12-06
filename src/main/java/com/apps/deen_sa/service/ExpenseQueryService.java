package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.ExpenseEntity;
import com.apps.deen_sa.repo.ExpenseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExpenseQueryService {

    private final ExpenseRepository repository;

    public ExpenseQueryService(ExpenseRepository repository) {
        this.repository = repository;
    }

    public List<ExpenseEntity> getRecentTransactions() {
        return repository.findTop5Expenses(PageRequest.of(0, 5));
    }

    public void deleteExpense(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Expense not found: " + id);
        }
        repository.deleteById(id);
    }
}
