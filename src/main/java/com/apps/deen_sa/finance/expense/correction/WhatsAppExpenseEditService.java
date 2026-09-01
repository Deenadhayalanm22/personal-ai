package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.conversation.*;
import com.apps.deen_sa.conversation.context.*;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.expense.*;
import com.apps.deen_sa.finance.legacy.state.*;
import com.apps.deen_sa.finance.tag.*;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.apps.deen_sa.web.WebApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class WhatsAppExpenseEditService {
    private final PendingActionContextRepository contexts;
    private final PendingActionContextService contextService;
    private final StateChangeRepository expenses;
    private final ExpenseCorrectionService corrections;
    private final ExpenseClassifier classifier;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final TransactionTagRepository transactionTags;

    public WhatsAppExpenseEditService(PendingActionContextRepository contexts,
            PendingActionContextService contextService, StateChangeRepository expenses,
            ExpenseCorrectionService corrections, ExpenseClassifier classifier,
            ExpenseTaxonomyRegistry taxonomy, TransactionTagRepository transactionTags) {
        this.contexts = contexts; this.contextService = contextService; this.expenses = expenses;
        this.corrections = corrections; this.classifier = classifier; this.taxonomy = taxonomy;
        this.transactionTags = transactionTags;
    }

    @Transactional
    public PendingActionContextService.ContextResponse create(Long userId, Long expenseId, String type) {
        if (!PendingActionContextService.EDIT_TRANSACTION.equals(type))
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT_TYPE",
                    "type must be EDIT_TRANSACTION");
        StateChangeEntity expense = expenses.findExpenseForUpdate(expenseId, userId.toString())
                .orElseThrow(() -> new WebApiException(HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND",
                        "Expense not found or no longer active"));
        if (expense.getRecordStatus() != ExpenseRecordStatus.ACTIVE)
            throw new WebApiException(HttpStatus.NOT_FOUND, "EXPENSE_NOT_FOUND",
                    "Expense not found or no longer active");
        return contextService.createExpenseEdit(userId, expenseId);
    }

    @Transactional
    public Optional<SpeechResult> processIfPending(String channel, String text, ConversationContext conversation) {
        if (!"WHATSAPP".equalsIgnoreCase(channel)) return Optional.empty();
        PendingActionContextEntity context = contexts.findActiveForUpdate(conversation.getUserId()).stream()
                .filter(value -> PendingActionContextService.EDIT_TRANSACTION.equals(value.getContextType()))
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .findFirst().orElse(null);
        if (context == null) return Optional.empty();

        String textLabel = taxonomy.canonicalAliasInText(text).orElse(null);
        String category = textLabel != null && taxonomy.isCategory(textLabel) ? textLabel : null;
        String subcategory = textLabel != null && taxonomy.isSubcategory(textLabel) ? textLabel : null;
        if (category == null && subcategory != null) category = taxonomy.parentCategory(subcategory).orElse(null);
        if (category == null || subcategory == null) {
            ExpenseDto extracted = classifier.extractExpense(text);
            if (category == null) category = canonical(extracted.getCategory());
            if (subcategory == null) subcategory = canonical(extracted.getSubcategory());
        }
        if (category == null && subcategory != null) category = taxonomy.parentCategory(subcategory).orElse(null);
        if (category == null || subcategory == null || !taxonomy.subcategoriesFor(category).contains(subcategory))
            return Optional.of(SpeechResult.invalid(
                    "Please mention a valid expense category and subcategory for this edit."));

        Long expenseId;
        try { expenseId = Long.valueOf(context.getContextValue()); }
        catch (NumberFormatException invalid) {
            context.setStatus(PendingActionContextStatus.REPLACED);
            context.setReplacedAt(Instant.now());
            return Optional.of(SpeechResult.invalid("This edit request is no longer valid."));
        }
        StateChangeEntity replacement = corrections.editClassification(
                conversation.getUserId(), expenseId, category, subcategory);
        copyTags(expenseId, replacement.getId());
        context.setStatus(PendingActionContextStatus.CONSUMED);
        context.setConsumedAt(Instant.now());
        conversation.reset();
        return Optional.of(SpeechResult.builder().status(SpeechStatus.SAVED)
                .message("Updated the expense to " + category + " / " + subcategory + ".")
                .savedEntity(replacement).needFollowup(false).build());
    }

    private String canonical(String value) {
        return taxonomy.canonicalLabel(value).or(() -> taxonomy.canonicalAlias(value)).orElse(null);
    }

    private void copyTags(Long originalId, Long replacementId) {
        Instant now = Instant.now();
        transactionTags.saveAll(transactionTags.findAllByTransactionId(originalId).stream().map(existing -> {
            TransactionTagEntity copy = new TransactionTagEntity();
            copy.setTransactionId(replacementId); copy.setTagId(existing.getTagId()); copy.setCreatedAt(now);
            return copy;
        }).toList());
    }
}
