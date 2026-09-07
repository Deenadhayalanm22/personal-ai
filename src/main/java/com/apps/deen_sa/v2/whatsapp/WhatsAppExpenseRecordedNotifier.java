package com.apps.deen_sa.v2.whatsapp;

import com.apps.deen_sa.conversation.MagicLinkService;
import com.apps.deen_sa.conversation.WhatsAppReplySender;
import com.apps.deen_sa.v2.dto.RecordedExpense;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhatsAppExpenseRecordedNotifier {
    private final WhatsAppReplySender replySender;
    private final MagicLinkService magicLinks;

    public void notify(RecordedExpense expense) {
        String merchant = expense.merchantName() == null
                ? ""
                : " at " + expense.merchantName();
        String portalLink = magicLinks.portalUrl();
        replySender.sendPortalLink(
                expense.externalUserId(),
                "Expense added successfully: ₹"
                        + expense.amount().stripTrailingZeros().toPlainString()
                        + merchant
                        + ".\n\nView or make changes: " + portalLink,
                portalLink);
    }
}
