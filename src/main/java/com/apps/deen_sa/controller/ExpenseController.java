package com.apps.deen_sa.controller;

import com.apps.deen_sa.dto.ExpenseDtoRequest;
import com.apps.deen_sa.service.ExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expense")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<String> recordExpenses(@RequestBody ExpenseDtoRequest request) {

        String response = expenseService.saveExpenseFromSpeech(request.getText());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }
}

