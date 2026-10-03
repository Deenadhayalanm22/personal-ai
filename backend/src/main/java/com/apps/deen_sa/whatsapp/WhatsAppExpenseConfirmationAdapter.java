package com.apps.deen_sa.whatsapp;

import com.apps.deen_sa.dto.ResponseAction;
import com.apps.deen_sa.service.WhatsAppReplySender;
import com.apps.deen_sa.dto.StoredDraftExtraction;
import com.apps.deen_sa.normalization.ExpenseConfirmationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WhatsAppExpenseConfirmationAdapter implements ExpenseConfirmationPort {
    private final WhatsAppReplySender replySender;

    @Override
    public void requestConfirmation(StoredDraftExtraction extraction) {
        String message = """
                Please confirm this expense:

                Amount: %s
                Category: %s
                Subcategory: %s
                Merchant: %s
                Source account: %s
                Date: %s
                """.formatted(
                displayAmount(extraction),
                display(extraction.categoryId()),
                display(extraction.subcategoryId()),
                display(extraction.merchantName()),
                display(extraction.sourceAccountName()),
                extraction.occurredAt());

        replySender.sendInteractiveReply(
                extraction.externalUserId(),
                message,
                List.of(
                        new ResponseAction(
                                "v2:expense:confirm:" + extraction.extractionId(), "Confirm"),
                        new ResponseAction(
                                "v2:expense:discard:" + extraction.extractionId(), "Discard")));
    }

    @Override
    public void sendCaptureInstruction(String externalUserId, String message) {
        replySender.sendTextReply(externalUserId, message);
    }

    private String displayAmount(StoredDraftExtraction extraction) {
        return extraction.amount() == null
                ? "Not identified"
                : "₹" + extraction.amount().stripTrailingZeros().toPlainString();
    }

    private String display(String value) {
        return value == null ? "Not identified" : value;
    }
}
