package com.apps.deen_sa.whatsapp;

import com.apps.deen_sa.service.MagicLinkService;
import com.apps.deen_sa.service.WhatsAppReplySender;
import com.apps.deen_sa.dto.RecordedExpense;
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
