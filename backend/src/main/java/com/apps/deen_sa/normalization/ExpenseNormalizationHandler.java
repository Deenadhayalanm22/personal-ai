package com.apps.deen_sa.normalization;

import com.apps.deen_sa.dto.DraftWriteResult;
import com.apps.deen_sa.dto.InboundMessage;
import com.apps.deen_sa.domain.InputType;
import com.apps.deen_sa.dto.NormalizedExpense;
import com.apps.deen_sa.service.TransactionDraftExtractionWriter;
import com.apps.deen_sa.service.MissingTransactionDateContextService;
import com.apps.deen_sa.service.AppUserService;
import com.apps.deen_sa.insights.WebExpenseCaptureService;
import java.util.List;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ExpenseNormalizationHandler {

    private final WebExpenseCaptureService capture;
    private final TransactionDraftExtractionWriter extractionWriter;
    private final ExpenseConfirmationPort confirmation;
    private final Clock clock;
    private final AppUserService users;
    private final MissingTransactionDateContextService dateContexts;

    public ExpenseNormalizationHandler(
            WebExpenseCaptureService capture,
            TransactionDraftExtractionWriter extractionWriter,
            ExpenseConfirmationPort confirmation,
            Clock clock,
            AppUserService users,
            MissingTransactionDateContextService dateContexts
    ) {
        this.capture = capture;
        this.extractionWriter = extractionWriter;
        this.confirmation = confirmation;
        this.clock = clock;
        this.users = users;
        this.dateContexts = dateContexts;
    }

    public void handle(DraftWriteResult draft, InboundMessage message) {
        if (!draft.created() || message.inputType() != InputType.TEXT) {
            return;
        }

        var user = users.resolve("WHATSAPP", message.externalUserId());
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
        var prepared = capture.prepare(user, today, List.of(message.rawContent()));
        if (!prepared.ready()) {
            extractionWriter.cancelWithoutExtraction(draft.draftId());
            confirmation.sendCaptureInstruction(message.externalUserId(), WebExpenseCaptureService.captureInstruction(prepared));
            return;
        }
        var facts = prepared.facts();
        LocalDate transactionDate = dateContexts.applyToDraft(draft.draftId(), message.rawContent(), facts.date());
        NormalizedExpense normalized = new NormalizedExpense(draft.draftId(), message.externalUserId(),
                facts.amount(), facts.category(), facts.subcategory(), facts.merchant(), facts.account(),
                transactionDate, BigDecimal.ONE);

        var committedExtraction = extractionWriter.saveActive(normalized);
        confirmation.requestConfirmation(committedExtraction);
    }

}
