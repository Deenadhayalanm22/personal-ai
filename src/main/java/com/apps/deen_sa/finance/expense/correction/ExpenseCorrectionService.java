package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.state.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.HashMap;

@Service
public class ExpenseCorrectionService {
    private final StateChangeRepository transactions;
    ExpenseCorrectionService(StateChangeRepository transactions) { this.transactions = transactions; }

    @Transactional public void voidExpense(Long userId, Long transactionId, int expectedVersion) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId, expectedVersion);
        original.setRecordStatus(ExpenseRecordStatus.VOIDED);
        original.setCorrectedAt(Instant.now()); original.setCorrectionReason("USER_DELETED_FROM_WEB");
        transactions.save(original);
    }

    @Transactional public StateChangeEntity editClassification(Long userId, Long transactionId, int expectedVersion,
                                                                 String category, String subcategory) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId, expectedVersion);
        StateChangeEntity replacement = copy(original);
        if (category != null) replacement.setCategory(category);
        if (subcategory != null) replacement.setSubcategory(subcategory);
        replacement = transactions.save(replacement);
        original.setRecordStatus(ExpenseRecordStatus.SUPERSEDED);
        original.setCorrectedAt(Instant.now()); original.setCorrectionReason("USER_EDITED_CLASSIFICATION_FROM_WEB");
        transactions.save(original);
        return replacement;
    }

    private StateChangeEntity activeOwnedExpense(Long userId, Long id, int version) {
        StateChangeEntity value = transactions.findExpenseForUpdate(id, userId.toString())
                .orElseThrow(() -> new IllegalArgumentException("That expense no longer exists."));
        if (value.getRecordStatus() != ExpenseRecordStatus.ACTIVE)
            throw new IllegalStateException("That expense was already corrected.");
        if (value.getRecordVersion() != version)
            throw new org.springframework.dao.OptimisticLockingFailureException("The expense changed since it was loaded.");
        return value;
    }

    private StateChangeEntity copy(StateChangeEntity source) {
        StateChangeEntity value = new StateChangeEntity();
        value.setUserId(source.getUserId()); value.setBusinessId(source.getBusinessId());
        value.setTransactionType(source.getTransactionType()); value.setAmount(source.getAmount());
        value.setQuantity(source.getQuantity()); value.setUnit(source.getUnit()); value.setCategory(source.getCategory());
        value.setSubcategory(source.getSubcategory()); value.setMainEntity(source.getMainEntity());
        value.setTimestamp(source.getTimestamp()); value.setRawText(source.getRawText());
        value.setDetails(source.getDetails() == null ? null : new HashMap<>(source.getDetails()));
        value.setCompletenessLevel(source.getCompletenessLevel()); value.setRecordStatus(ExpenseRecordStatus.ACTIVE);
        value.setRootTransactionId(source.getRootTransactionId() == null ? source.getId() : source.getRootTransactionId());
        value.setReplacesTransactionId(source.getId()); value.setRecordVersion(source.getRecordVersion() + 1);
        return value;
    }
}
