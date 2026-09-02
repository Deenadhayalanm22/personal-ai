package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.conversation.WhatsAppReplySender;
import com.apps.deen_sa.v2.dto.RecordedExpense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhatsAppExpenseRecordedNotifier {
    private final WhatsAppReplySender replySender;

    public void notify(RecordedExpense expense) {
        String merchant = expense.merchantName() == null
                ? ""
                : " at " + expense.merchantName();
        replySender.sendTextReply(
                expense.externalUserId(),
                "Expense added successfully: ₹"
                        + expense.amount().stripTrailingZeros().toPlainString()
                        + merchant + ".");
    }
}
