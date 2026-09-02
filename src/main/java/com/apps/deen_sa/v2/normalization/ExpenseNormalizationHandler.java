package com.apps.deen_sa.v2.normalization;

import com.apps.deen_sa.v2.dto.DraftWriteResult;
import com.apps.deen_sa.v2.dto.InboundMessage;
import com.apps.deen_sa.v2.domain.InputType;
import com.apps.deen_sa.v2.dto.NormalizedExpense;
import com.apps.deen_sa.v2.service.TransactionDraftExtractionWriter;
import com.apps.deen_sa.v2.service.V2MissingTransactionDateContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ExpenseNormalizationHandler {
    private static final ZoneId DEFAULT_USER_ZONE = ZoneId.of("Asia/Kolkata");

    private final ExpenseNormalizationPort normalizer;
    private final TransactionDraftExtractionWriter extractionWriter;
    private final ExpenseConfirmationPort confirmation;
    private final Clock clock;
    private final V2MissingTransactionDateContextService dateContexts;

    public void handle(DraftWriteResult draft, InboundMessage message) {
        if (!draft.created() || message.inputType() != InputType.TEXT) {
            return;
        }

        LocalDate today = LocalDate.now(clock.withZone(DEFAULT_USER_ZONE));
        ExpenseNormalizationPort.ExpenseFacts facts =
                normalizer.normalize(message.externalUserId(), message.rawContent(), today);
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
                transactionDate,
                facts.confidence());

        var committedExtraction = extractionWriter.saveActive(normalized);
        confirmation.requestConfirmation(committedExtraction);
    }
}
