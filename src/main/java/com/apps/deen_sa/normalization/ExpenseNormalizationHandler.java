package com.apps.deen_sa.normalization;

import com.apps.deen_sa.dto.DraftWriteResult;
import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.dto.NormalizedExpense;
import com.apps.deen_sa.service.TransactionDraftExtractionWriter;
import com.apps.deen_sa.service.MissingTransactionDateContextService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ExpenseNormalizationHandler {
    private static final ZoneId DEFAULT_USER_ZONE = ZoneId.of("Asia/Kolkata");

    private final ExpenseNormalizationPort normalizer;
    private final TransactionDraftExtractionWriter extractionWriter;
    private final ExpenseConfirmationPort confirmation;
    private final Clock clock;
    private final MissingTransactionDateContextService dateContexts;
    private final BigDecimal minimumExpenseConfidence;

    public ExpenseNormalizationHandler(
            ExpenseNormalizationPort normalizer,
            TransactionDraftExtractionWriter extractionWriter,
            ExpenseConfirmationPort confirmation,
            Clock clock,
            MissingTransactionDateContextService dateContexts,
            @Value("${openai.escalation-confidence:0.55}") BigDecimal minimumExpenseConfidence
    ) {
        this.normalizer = normalizer;
        this.extractionWriter = extractionWriter;
        this.confirmation = confirmation;
        this.clock = clock;
        this.dateContexts = dateContexts;
        this.minimumExpenseConfidence = minimumExpenseConfidence;
    }

    public void handle(DraftWriteResult draft, InboundMessage message) {
        if (!draft.created() || message.inputType() != InputType.TEXT) {
            return;
        }

        LocalDate today = LocalDate.now(clock.withZone(DEFAULT_USER_ZONE));
        ExpenseNormalizationPort.ExpenseFacts facts =
                normalizer.normalize(message.externalUserId(), message.rawContent(), today);
        if (isLowConfidenceNonExpense(facts)) {
            extractionWriter.cancelWithoutExtraction(draft.draftId());
            confirmation.sendExpenseInstruction(message.externalUserId());
            return;
        }

        LocalDate transactionDate = facts.transactionDate() == null
                ? today
                : facts.transactionDate();
        transactionDate = dateContexts.applyToDraft(
                draft.draftId(), message.rawContent(), transactionDate);

        NormalizedExpense normalized = new NormalizedExpense(
                draft.draftId(),
                message.externalUserId(),
                facts.amount(),
                facts.category(),
                facts.subcategory(),
                facts.merchant(),
                facts.sourceAccount(),
                transactionDate,
                facts.confidence());

        var committedExtraction = extractionWriter.saveActive(normalized);
        confirmation.requestConfirmation(committedExtraction);
    }

    private boolean isLowConfidenceNonExpense(ExpenseNormalizationPort.ExpenseFacts facts) {
        boolean hasExpenseDetails = facts.amount() != null
                || facts.category() != null
                || facts.subcategory() != null
                || facts.merchant() != null
                || facts.sourceAccount() != null;
        boolean isLowConfidence = facts.confidence() == null
                || facts.confidence().compareTo(minimumExpenseConfidence) < 0;
        return !hasExpenseDetails && isLowConfidence;
    }
}
