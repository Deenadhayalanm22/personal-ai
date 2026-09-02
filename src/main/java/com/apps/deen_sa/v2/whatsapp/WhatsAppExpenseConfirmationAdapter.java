package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.conversation.ResponseAction;
import com.apps.deen_sa.conversation.WhatsAppReplySender;
import com.apps.deen_sa.v2.dto.StoredDraftExtraction;
import com.apps.deen_sa.v2.normalization.ExpenseConfirmationPort;
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
                Date: %s
                """.formatted(
                displayAmount(extraction),
                display(extraction.categoryId()),
                display(extraction.subcategoryId()),
                display(extraction.merchantName()),
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

    private String displayAmount(StoredDraftExtraction extraction) {
        return extraction.amount() == null
                ? "Not identified"
                : "₹" + extraction.amount().stripTrailingZeros().toPlainString();
    }

    private String display(String value) {
        return value == null ? "Not identified" : value;
    }
}
