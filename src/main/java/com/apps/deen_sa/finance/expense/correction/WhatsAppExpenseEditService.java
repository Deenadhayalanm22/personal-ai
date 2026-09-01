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
                .filter(value -> PendingActionContextService.EDIT_TRANSACTION.equals(value.getContextType())
                        || PendingActionContextService.CONFIRM_EDIT_TRANSACTION.equals(value.getContextType()))
                .filter(value -> value.getExpiresAt().isAfter(Instant.now()))
                .findFirst().orElse(null);
        if (context == null) return Optional.empty();

        if (PendingActionContextService.CONFIRM_EDIT_TRANSACTION.equals(context.getContextType()))
            return Optional.of(confirmOrCancel(text, conversation, context));

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
        StateChangeEntity original = expenses.findExpenseForUpdate(expenseId, conversation.getUserId().toString())
                .filter(value -> value.getRecordStatus() == ExpenseRecordStatus.ACTIVE)
                .orElse(null);
        if (original == null) {
            context.setStatus(PendingActionContextStatus.REPLACED);
            context.setReplacedAt(Instant.now());
            return Optional.of(SpeechResult.invalid("That expense no longer exists."));
        }
        context.setContextType(PendingActionContextService.CONFIRM_EDIT_TRANSACTION);
        context.setContextValue(expenseId + "\n" + category + "\n" + subcategory);
        return Optional.of(review(original, text, category, subcategory));
    }

    private SpeechResult confirmOrCancel(String text, ConversationContext conversation,
                                         PendingActionContextEntity context) {
        String answer = text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
        if (Set.of("CANCEL", "DISCARD", "CANCEL_EXPENSE_EDIT").contains(answer)) {
            context.setStatus(PendingActionContextStatus.CONSUMED);
            context.setConsumedAt(Instant.now());
            conversation.reset();
            return SpeechResult.info("Cancelled. The expense was not changed.");
        }
        if (!Set.of("CONFIRM", "YES", "CONFIRM_EXPENSE_EDIT").contains(answer))
            return confirmationPrompt("Please confirm or cancel this expense update.");

        String[] proposal = context.getContextValue().split("\n", -1);
        if (proposal.length != 3) {
            context.setStatus(PendingActionContextStatus.REPLACED);
            context.setReplacedAt(Instant.now());
            return SpeechResult.invalid("This edit request is no longer valid.");
        }
        Long expenseId;
        try { expenseId = Long.valueOf(proposal[0]); }
        catch (NumberFormatException invalid) {
            context.setStatus(PendingActionContextStatus.REPLACED);
            context.setReplacedAt(Instant.now());
            return SpeechResult.invalid("This edit request is no longer valid.");
        }
        StateChangeEntity replacement = corrections.editClassification(
                conversation.getUserId(), expenseId, proposal[1], proposal[2]);
        copyTags(expenseId, replacement.getId());
        context.setStatus(PendingActionContextStatus.CONSUMED);
        context.setConsumedAt(Instant.now());
        conversation.reset();
        return SpeechResult.builder().status(SpeechStatus.SAVED)
                .message("Confirmed. Updated the expense to " + proposal[1] + " / " + proposal[2] + ".")
                .savedEntity(replacement).needFollowup(false).build();
    }

    private SpeechResult review(StateChangeEntity original, String proposedMessage,
                                String category, String subcategory) {
        String oldCategory = display(original.getCategory());
        String oldSubcategory = display(original.getSubcategory());
        String message = "Please review this expense update:\n\n"
                + "*Old message*\n" + display(original.getRawText()) + "\n\n"
                + "*New message*\n" + display(proposedMessage) + "\n\n"
                + "*Old expense*\n" + details(original, oldCategory, oldSubcategory) + "\n\n"
                + "*New expense*\n" + details(original,
                changed(oldCategory, category), changed(oldSubcategory, subcategory))
                + "\n\nOnly the struck-out values will be replaced. Confirm this change?";
        return confirmationPrompt(message);
    }

    private SpeechResult confirmationPrompt(String message) {
        return SpeechResult.followup(message, List.of("confirmation"), null, List.of(
                new ResponseAction("answer:CONFIRM_EXPENSE_EDIT", "Confirm"),
                new ResponseAction("answer:CANCEL_EXPENSE_EDIT", "Cancel")));
    }

    private String details(StateChangeEntity value, String category, String subcategory) {
        Object source = value.getDetails() == null ? null : value.getDetails().get("paymentSource");
        return "Amount: " + display(value.getAmount())
                + "\nDate: " + display(value.getTimestamp())
                + "\nCategory: " + category
                + "\nSubcategory: " + subcategory
                + "\nMerchant: " + display(value.getMainEntity())
                + "\nPayment source: " + display(source);
    }

    private String changed(String oldValue, String newValue) {
        return oldValue.equals(newValue) ? newValue : "~" + oldValue + "~ → *" + newValue + "*";
    }

    private String display(Object value) {
        return value == null || value.toString().isBlank() ? "Not provided" : value.toString();
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
