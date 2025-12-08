package com.apps.deen_sa.handler;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.entity.ExpenseEntity;
import com.apps.deen_sa.llm.ExpenseClassifier;
import com.apps.deen_sa.mapper.ExpenseDtoToEntityMapper;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechHandler;
import com.apps.deen_sa.orchestrator.SpeechResult;
import com.apps.deen_sa.repo.ExpenseRepository;
import com.apps.deen_sa.util.ExpenseMerger;
import com.apps.deen_sa.validator.ExpenseValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseHandler implements SpeechHandler {

    private final ExpenseClassifier llm;
    private final ExpenseRepository repo;

    @Override
    public String intentType() {
        return "EXPENSE";
    }

    /**
     * PRIMARY ENTRY POINT: first time user speaks an expense sentence
     */
    @Override
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {

        ExpenseDto dto = llm.extractExpense(text);

        List<String> missing = ExpenseValidator.findMissingFields(dto);

        // Missing values → start follow-up mode
        if (!missing.isEmpty()) {

            String nextField = missing.getFirst();
            String followupQ = llm.generateFollowupQuestionForExpense(nextField, dto);

            // update context
            ctx.setActiveIntent("EXPENSE");
            ctx.setWaitingForField(nextField);
            ctx.setPartialObject(dto);

            return SpeechResult.followup(
                    followupQ,
                    List.of(nextField),
                    dto
            );
        }

        // No missing fields → save immediately
        ExpenseEntity saved = saveExpense(dto);
        ctx.reset();

        return SpeechResult.saved(saved);
    }

    /**
     * FOLLOW-UP HANDLER: user answers missing fields
     */
    @Override
    public SpeechResult handleFollowup(String userAnswer, ConversationContext ctx) {

        String missingField = ctx.getWaitingForField();
        ExpenseDto dto = (ExpenseDto) ctx.getPartialObject();

        // Step A – Ask LLM to refine missing field
        ExpenseDto refinedField = llm.extractFieldFromFollowup(dto, missingField, userAnswer);

        // Step B – Merge into DTO
        ExpenseMerger.merge(dto, refinedField);
        dto.setRawText(dto.getRawText() + " " + userAnswer);

        // Step C – Check again
        List<String> missing = ExpenseValidator.findMissingFields(dto);

        if (!missing.isEmpty()) {
            String nextField = missing.getFirst();
            String followupQ = llm.generateFollowupQuestionForExpense(nextField, dto);

            ctx.setWaitingForField(nextField);
            ctx.setPartialObject(dto);

            return SpeechResult.followup(
                    followupQ,
                    List.of(nextField),
                    dto
            );
        }

        // Step D – Save when complete
        ExpenseEntity saved = saveExpense(dto);

        ctx.reset();
        return SpeechResult.saved(saved);
    }

    // -----------------------------------------------------
    // INTERNAL SAVE LOGIC
    // -----------------------------------------------------
    private ExpenseEntity saveExpense(ExpenseDto dto) {
        // TODO: Resolve userId properly (from JWT/session)
        Long userId = 1L; // placeholder
        return repo.save(ExpenseDtoToEntityMapper.toEntity(dto, userId));
    }
}
