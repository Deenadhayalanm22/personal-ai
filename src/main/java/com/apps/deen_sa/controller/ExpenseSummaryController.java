package com.apps.deen_sa.controller;


import com.apps.deen_sa.dto.ExpenseSummaryDto;
import com.apps.deen_sa.service.ExpenseSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expense/summary")
public class ExpenseSummaryController {

    private final ExpenseSummaryService summaryService;

    public ExpenseSummaryController(ExpenseSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping
    public ResponseEntity<ExpenseSummaryDto> getSummary() {
        return ResponseEntity.ok(summaryService.getDashboardSummary());
    }
}
