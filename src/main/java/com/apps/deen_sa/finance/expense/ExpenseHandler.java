package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.expense.draft.ExpenseDraftService;
import com.apps.deen_sa.conversation.context.PendingActionContextService;
import com.apps.deen_sa.finance.legacy.state.*;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ExpenseHandler implements SpeechHandler {
    private static final String CONFIRM_EXPENSE = "confirmExpense";
    private final ExpenseClassifier llm;
    private final StateChangeRepository repository;
    private final ExpenseCompletenessEvaluator completeness;
    private final ExpenseInputNormalizer normalizer;
    private final ObjectMapper objectMapper;
    private final TaxonomyCandidateService taxonomyCandidates;
    private final ExpenseDraftService drafts;
    private final PendingActionContextService actionContexts;

    @Override public String intentType() { return "EXPENSE"; }
    @Override public SpeechResult handleSpeech(String text, ConversationContext context) {
        ExpenseDto dto = llm.extractExpense(text);
        actionContexts.attachToNextExpense(dto, text, context.getUserId(), context.getChannel());
        return prepare(normalizer.normalize(dto, text, context), context);
    }
    public SpeechResult handleInterpreted(EventPatch patch, String rawText, ConversationContext context) {
        ExpenseDto dto = objectMapper.convertValue(patch.fields().asMap(), ExpenseDto.class);
        dto.setValid(dto.getAmount() != null);
        actionContexts.attachToNextExpense(dto, rawText, context.getUserId(), context.getChannel());
        return prepare(normalizer.normalize(dto, rawText, context), context);
    }
    @Transactional
    public SpeechResult handleInterpretedFollowup(EventPatch patch, String answer, ConversationContext context) {
        if (CONFIRM_EXPENSE.equals(context.getWaitingForField())) return confirm(answer, context);
        return mergeFollowup(objectMapper.convertValue(patch.fields().asMap(), ExpenseDto.class), answer, context);
    }
    @Override @Transactional public SpeechResult handleFollowup(String answer, ConversationContext context) {
        if (CONFIRM_EXPENSE.equals(context.getWaitingForField())) return confirm(answer, context);
        ExpenseDto current = (ExpenseDto) context.getPartialObject();
        return mergeFollowup(llm.extractFieldFromFollowup(current, context.getWaitingForField(), answer), answer, context);
    }
    private SpeechResult mergeFollowup(ExpenseDto refined, String answer, ConversationContext context) {
        ExpenseDto current = (ExpenseDto) context.getPartialObject();
        ExpenseMerger.merge(current, refined);
        String raw = (current.getRawText() == null ? "" : current.getRawText() + " ") + answer;
        return prepare(normalizer.normalize(current, raw, context), context);
    }
    private SpeechResult prepare(ExpenseDto dto, ConversationContext context) {
        CompletenessLevelEnum level = completeness.evaluate(dto);
        List<String> missing = ExpenseValidator.findMissingFields(dto);
        drafts.capture(dto, context, missing);
        if (level == null || !missing.isEmpty()) {
            String field = missing.isEmpty() ? "amount" : missing.getFirst();
            context.setActiveIntent("EXPENSE"); context.setWaitingForField(field); context.setPartialObject(dto);
            return SpeechResult.followup(question(field), List.of(field), dto);
        }
        context.setActiveIntent("EXPENSE"); context.setWaitingForField(CONFIRM_EXPENSE); context.setPartialObject(dto);
        return SpeechResult.followup(preview(dto), List.of(CONFIRM_EXPENSE), dto, List.of(
                new ResponseAction("answer:CONFIRM_EXPENSE", "Confirm"),
                new ResponseAction("answer:DISCARD_EXPENSE", "Discard")));
    }
    @Transactional
    protected SpeechResult confirm(String answer, ConversationContext context) {
        if ("DISCARD_EXPENSE".equalsIgnoreCase(answer) || "cancel".equalsIgnoreCase(answer)) {
            context.reset(); return SpeechResult.info("Discarded. No expense was saved.");
        }
        if (!"CONFIRM_EXPENSE".equalsIgnoreCase(answer) && !"confirm".equalsIgnoreCase(answer)) {
            return SpeechResult.followup("Please confirm or discard this expense.", List.of(CONFIRM_EXPENSE),
                    context.getPartialObject(), List.of(new ResponseAction("answer:CONFIRM_EXPENSE", "Confirm"),
                            new ResponseAction("answer:DISCARD_EXPENSE", "Discard")));
        }
        ExpenseDto dto = (ExpenseDto) context.getPartialObject();
        boolean contextConsumed = actionContexts.consumeIfActive(context.getUserId(), dto.getPendingActionContextId());
        if (dto.isContextDateApplied() && !contextConsumed) {
            dto.setTransactionDate(LocalDate.now(ZoneId.of(context.getTimezone())));
            dto.setContextDateApplied(false);
        }
        StateChangeEntity saved = repository.save(ExpenseDtoToEntityMapper.toEntity(dto, context.getUserId()));
        taxonomyCandidates.recordIfUseful(dto, saved.getId());
        if (context.getActiveDraftId() != null)
            drafts.complete(context.getUserId(), context.getActiveDraftId(), saved.getId());
        context.reset();
        return SpeechResult.builder().status(SpeechStatus.SAVED)
                .message(confirmation(dto))
                .savedEntity(saved).needFollowup(false).build();
    }
    private String confirmation(ExpenseDto dto) {
        String description = dto.getSubcategory() != null ? dto.getSubcategory()
                : dto.getCategory() != null ? dto.getCategory()
                : dto.getMerchantName() != null ? dto.getMerchantName() : "expense";
        String date = dto.getTransactionDate().format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH));
        return "Added ₹" + dto.getAmount().stripTrailingZeros().toPlainString()
                + " for " + description + " on " + date + ".";
    }
    private String preview(ExpenseDto dto) {
        return "Amount: ₹" + dto.getAmount().stripTrailingZeros().toPlainString()
                + "\nCategory: " + dto.getCategory() + "\nSubcategory: " + dto.getSubcategory()
                + "\nMerchant: " + (dto.getMerchantName() == null ? "Not provided" : dto.getMerchantName())
                + "\nDate: " + dto.getTransactionDate()
                + "\nPayment source: " + (dto.getSourceAccount() == null ? "Not provided" : dto.getSourceAccount())
                + "\n\nConfirm this expense?";
    }
    private String question(String field) {
        return switch (field) {
            case "amount" -> "How much did you spend?";
            case "category" -> "What category was this expense?";
            case "subcategory" -> "What subcategory best describes it?";
            default -> "Please provide " + field + ".";
        };
    }
}
