package com.apps.deen_sa.service;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.entity.ExpenseEntity;
import com.apps.deen_sa.mapper.ExpenseDtoToEntityMapper;
import com.apps.deen_sa.repo.ExpenseRepository;
import org.springframework.stereotype.Service;

@Service
public class ExpenseService {

    private final ExpenseLLMService llmService;
    private final ExpenseRepository repository;

    public ExpenseService(ExpenseLLMService llmService,
                          ExpenseRepository repository) {
        this.llmService = llmService;
        this.repository = repository;
    }

    public String saveExpenseFromSpeech(String spokenText) {
        // 1. Extract structured data using LLM
        ExpenseDto dto = llmService.extractExpense(spokenText);

        // 2. Attach raw text so we can store it in DB
        dto.setRawText(spokenText);

        // 3. Convert DTO → Entity
        ExpenseEntity entity = ExpenseDtoToEntityMapper.toEntity(dto);

        // 4. Save to Postgres
        repository.save(entity);

        return "Transaction captured";
    }
}

