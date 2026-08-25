package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.mutation.MutationTypeEnum;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationEntity;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationRepository;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationService;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseCorrectionService {
    private final StateChangeRepository transactions;
    private final StateMutationRepository mutationRecords;
    private final StateMutationService mutations;
    private final StateContainerRepository containers;

    ExpenseCorrectionService(StateChangeRepository transactions, StateMutationRepository mutationRecords,
                             StateMutationService mutations, StateContainerRepository containers) {
        this.transactions = transactions;
        this.mutationRecords = mutationRecords;
        this.mutations = mutations;
        this.containers = containers;
    }

    @Transactional
    public void voidExpense(Long userId, Long transactionId, int expectedVersion) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId, expectedVersion);
        reverseLegacyImpact(original);
        original.setRecordStatus(ExpenseRecordStatus.VOIDED);
        original.setCorrectedAt(Instant.now());
        original.setCorrectionReason("USER_DELETED_FROM_WEB");
        transactions.save(original);
    }

    @Transactional
    public StateChangeEntity editClassification(Long userId, Long transactionId, int expectedVersion,
                                                 String category, String subcategory) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId, expectedVersion);
        StateChangeEntity replacement = copy(original);
        if (category != null) replacement.setCategory(category);
        if (subcategory != null) replacement.setSubcategory(subcategory);
        replacement = transactions.save(replacement);

        if (original.isFinanciallyApplied()) {
            reverseLegacyImpact(original);
            applyLegacyImpact(replacement, original);
        }
        original.setRecordStatus(ExpenseRecordStatus.SUPERSEDED);
        original.setCorrectedAt(Instant.now());
        original.setCorrectionReason("USER_EDITED_CLASSIFICATION_FROM_WEB");
        transactions.save(original);
        return replacement;
    }

    private StateChangeEntity activeOwnedExpense(Long userId, Long transactionId) {
        StateChangeEntity value = transactions.findExpenseForUpdate(transactionId, userId.toString())
                .orElseThrow(() -> new IllegalArgumentException("That expense no longer exists."));
        if (value.getRecordStatus() != ExpenseRecordStatus.ACTIVE)
            throw new IllegalStateException("That expense was already corrected. Please choose its latest version.");
        return value;
    }

    private StateChangeEntity activeOwnedExpense(Long userId, Long transactionId, int expectedVersion) {
        StateChangeEntity value = activeOwnedExpense(userId, transactionId);
        if (value.getRecordVersion() != expectedVersion)
            throw new org.springframework.dao.OptimisticLockingFailureException("The expense changed since it was loaded.");
        return value;
    }

    private void reverseLegacyImpact(StateChangeEntity original) {
        if (!original.isFinanciallyApplied()) return;
        List<StateMutationEntity> applied = mutationRecords.findByTransactionIdOrderByIdAsc(original.getId()).stream()
                .filter(value -> value.getAmount() != null && value.getAmount().signum() > 0)
                .toList();
        if (applied.isEmpty()) throw new IllegalStateException("The expense balance impact cannot be safely reversed.");
        for (StateMutationEntity record : applied) {
            StateContainerEntity container = containers.findById(record.getContainerId())
                    .orElseThrow(() -> new IllegalStateException("The affected account no longer exists."));
            StateMutationCommand command = new StateMutationCommand(record.getAmount(), record.getAdjustmentType(),
                    record.getReason(), original.getId(), record.getOccurredAt());
            mutations.reverse(container, command, "EXPENSE_CORRECTION_REVERSAL");
        }
    }

    private void applyLegacyImpact(StateChangeEntity replacement, StateChangeEntity original) {
        if (!original.isFinanciallyApplied()) {
            replacement.setFinanciallyApplied(false);
            return;
        }
        StateContainerEntity target = containers.findById(replacement.getSourceContainerId())
                .orElseThrow(() -> new IllegalStateException("The selected account no longer exists."));
        MutationTypeEnum type = mutationRecords.findByTransactionIdOrderByIdAsc(original.getId()).stream()
                .filter(value -> value.getAmount() != null && value.getAmount().signum() > 0)
                .map(StateMutationEntity::getAdjustmentType).findFirst().orElse(MutationTypeEnum.DEBIT);
        mutations.apply(target, new StateMutationCommand(replacement.getAmount(), type,
                "EXPENSE_CORRECTION_REPLACEMENT", replacement.getId(), Instant.now()));
        replacement.setFinanciallyApplied(true);
    }

    private StateChangeEntity copy(StateChangeEntity source) {
        StateChangeEntity value = new StateChangeEntity();
        value.setUserId(source.getUserId()); value.setBusinessId(source.getBusinessId());
        value.setTransactionType(source.getTransactionType()); value.setAmount(source.getAmount());
        value.setQuantity(source.getQuantity()); value.setUnit(source.getUnit()); value.setCategory(source.getCategory());
        value.setSubcategory(source.getSubcategory()); value.setMainEntity(source.getMainEntity());
        value.setTimestamp(source.getTimestamp()); value.setRawText(source.getRawText());
        value.setDetails(source.getDetails() == null ? null : new HashMap<>(source.getDetails()));
        value.setSourceContainerId(source.getSourceContainerId()); value.setTargetContainerId(source.getTargetContainerId());
        value.setCompletenessLevel(source.getCompletenessLevel()); value.setFinanciallyApplied(source.isFinanciallyApplied());
        value.setNeedsEnrichment(source.isNeedsEnrichment()); value.setRecordStatus(ExpenseRecordStatus.ACTIVE);
        value.setRootTransactionId(source.getRootTransactionId() == null ? source.getId() : source.getRootTransactionId());
        value.setReplacesTransactionId(source.getId()); value.setRecordVersion(source.getRecordVersion() + 1);
        return value;
    }

}
