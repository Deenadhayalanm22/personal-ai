package com.apps.deen_sa.web;

import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class WebAccountEnrichmentService {
    private final StateContainerRepository accounts;
    private final StateContainerService containers;
    private final DashboardAccountService dashboard;

    public WebAccountEnrichmentService(StateContainerRepository accounts, StateContainerService containers,
                                       DashboardAccountService dashboard) {
        this.accounts = accounts; this.containers = containers; this.dashboard = dashboard;
    }

    public DashboardAccountService.DashboardAccount enrich(Long userId, Long accountId, AccountEnrichment request) {
        StateContainerEntity value = accounts.findById(accountId)
                .filter(account -> userId.equals(account.getOwnerId()) && "ACTIVE".equals(account.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account details are required");
        if (request.currentBalance() != null) {
            if (request.currentBalance().signum() < 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current balance cannot be negative");
            value.setCurrentValue(request.currentBalance()); value.setAvailableValue(request.currentBalance());
        }
        if (request.creditLimit() != null) {
            if (!"CREDIT_CARD".equals(value.getContainerType()) || request.creditLimit().signum() <= 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Credit limit must be positive and belongs to a credit card");
            value.setCapacityLimit(request.creditLimit());
        }
        Map<String, Object> details = value.getDetails() == null ? new HashMap<>() : new HashMap<>(value.getDetails());
        putDay(details, "billingDay", request.billingDay()); putDay(details, "dueDay", request.dueDay());
        value.setDetails(details);
        containers.UpdateValueContainer(value);
        return dashboard.single(value);
    }

    private void putDay(Map<String, Object> details, String field, Integer value) {
        if (value == null) return;
        if (value < 1 || value > 31)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be between 1 and 31");
        details.put(field, value);
    }

    public record AccountEnrichment(BigDecimal currentBalance, BigDecimal creditLimit,
                                    Integer billingDay, Integer dueDay) { }
}
