package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.service.CreditCardBillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.YearMonth;
import java.util.*;

/** FIN-EPIC-003/005: captured bills use the same calculation as Accounts, never issuer balances. */
@Service
public class CreditCardBillsTool {
    private final CreditCardBillService bills;
    public CreditCardBillsTool(CreditCardBillService bills) { this.bills = bills; }

    @Transactional(readOnly = true)
    public FinancialRecordsTool.Result read(AppUserEntity user, Request request) {
        if (request == null || request.limit() < 1 || request.limit() > 50 || request.offset() < 0 || request.offset() > 10000)
            throw ExpenseQueryTool.invalid("Choose a bill month and pagination with limit 1–50 and offset 0–10000.");
        YearMonth month;
        try { month = YearMonth.parse(request.month()); }
        catch (RuntimeException ex) { throw ExpenseQueryTool.invalid("Use YYYY-MM for the bill due month."); }
        var page = bills.summaries(user, month, request.limit(), request.offset());
        var rows = new ArrayList<Map<String, Object>>();
        for (var bill : page.bills()) {
            var row = new LinkedHashMap<String, Object>();
            row.put("card_id", bill.cardId()); row.put("name", bill.cardName()); row.put("month", bill.month());
            row.put("period_start", bill.periodStart()); row.put("statement_end", bill.statementEnd());
            row.put("statement_generated_at", bill.statementGeneratedAt()); row.put("due_date", bill.dueDate());
            row.put("captured_bill_amount", bill.projectedAmount()); row.put("paid_amount", bill.paidAmount());
            row.put("remaining_amount", bill.remaining()); row.put("monthly_purchase_amount", bill.monthlyPurchaseAmount());
            row.put("unmatched_payment_amount", bill.unmatchedPaymentAmount()); row.put("statement_closed", bill.statementClosed());
            rows.add(Collections.unmodifiableMap(row));
        }
        int end = request.offset() + rows.size();
        boolean truncated = end < page.matchingCount();
        return new FinancialRecordsTool.Result("records", "credit_card_bills", "records", user.getCurrency(),
                page.matchingCount(), List.copyOf(rows), truncated, truncated ? end : null,
                "Bill due month: " + month + ". Captured purchases only, not a verified issuer statement. "
                + "Monthly purchases and statement bills use different periods; do not add them as spending. "
                + "Payments never create expenses or update a bank balance. Remaining is floored at zero; "
                + "unmatched payments are recorded payments above captured purchases, not an inferred balance. "
                + "Open statements remain projected. Card start month is the first included due month. "
                + "For dated settlements, read credit_cards history using card_id. Review or record payments in Home > Your money > Accounts.");
    }
    public record Request(String month, int limit, int offset) {}
}
