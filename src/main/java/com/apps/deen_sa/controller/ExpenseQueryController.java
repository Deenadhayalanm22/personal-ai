package com.apps.deen_sa.controller;

import com.apps.deen_sa.entity.ExpenseEntity;
import com.apps.deen_sa.service.ExpenseQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expense")
public class ExpenseQueryController {

    private final ExpenseQueryService service;

    public ExpenseQueryController(ExpenseQueryService service) {
        this.service = service;
    }

    // ---- GET recent 5 transactions ----
    @GetMapping("/recent")
    public ResponseEntity<List<ExpenseEntity>> getRecent() {
        return ResponseEntity.ok(service.getRecentTransactions());
    }

    // ---- DELETE an expense by ID ----
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        service.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }
}
