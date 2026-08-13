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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
class ExpenseCorrectionService {
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
    CorrectionOutcome voidExpense(Long userId, Long transactionId) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId);
        reverseLegacyImpact(original);
        original.setRecordStatus(ExpenseRecordStatus.VOIDED);
        original.setCorrectedAt(Instant.now());
        original.setCorrectionReason("USER_DELETED");
        transactions.save(original);
        return new CorrectionOutcome(original, null, balanceImpact(original.getAmount(), BigDecimal.ZERO));
    }

    @Transactional
    CorrectionOutcome editExpense(Long userId, Long transactionId, CorrectionField field, Object value) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId);
        StateChangeEntity replacement = copy(original);
        apply(replacement, field, value);
        replacement = transactions.save(replacement);

        if (original.isFinanciallyApplied()) {
            reverseLegacyImpact(original);
            applyLegacyImpact(replacement, original);
        }

        original.setRecordStatus(ExpenseRecordStatus.SUPERSEDED);
        original.setCorrectedAt(Instant.now());
        original.setCorrectionReason("USER_EDITED_" + field.name());
        transactions.save(original);
        final StateChangeEntity savedReplacement = replacement;

        return new CorrectionOutcome(original, savedReplacement,
                balanceImpact(original.getAmount(), replacement.getAmount()));
    }

    private StateChangeEntity activeOwnedExpense(Long userId, Long transactionId) {
        StateChangeEntity value = transactions.findExpenseForUpdate(transactionId, userId.toString())
                .orElseThrow(() -> new IllegalArgumentException("That expense no longer exists."));
        if (value.getRecordStatus() != ExpenseRecordStatus.ACTIVE)
            throw new IllegalStateException("That expense was already corrected. Please choose its latest version.");
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
        value.setTags(source.getTags() == null ? null : List.copyOf(source.getTags()));
        value.setSourceContainerId(source.getSourceContainerId()); value.setTargetContainerId(source.getTargetContainerId());
        value.setCompletenessLevel(source.getCompletenessLevel()); value.setFinanciallyApplied(source.isFinanciallyApplied());
        value.setNeedsEnrichment(source.isNeedsEnrichment()); value.setRecordStatus(ExpenseRecordStatus.ACTIVE);
        value.setRootTransactionId(source.getRootTransactionId() == null ? source.getId() : source.getRootTransactionId());
        value.setReplacesTransactionId(source.getId()); value.setRecordVersion(source.getRecordVersion() + 1);
        return value;
    }

    private void apply(StateChangeEntity value, CorrectionField field, Object replacement) {
        switch (field) {
            case AMOUNT -> value.setAmount((BigDecimal) replacement);
            case CATEGORY -> value.setCategory(replacement.toString());
            case MERCHANT -> value.setMainEntity(replacement.toString());
            case DATE -> value.setTimestamp((Instant) replacement);
            case ACCOUNT -> value.setSourceContainerId((Long) replacement);
        }
    }

    private String accountName(Long id) { return id == null ? "Unallocated" : containers.findById(id).map(StateContainerEntity::getName).orElse("Unknown"); }
    private String balanceImpact(BigDecimal oldAmount, BigDecimal newAmount) {
        BigDecimal restored = oldAmount.subtract(newAmount);
        if (restored.signum() == 0) return null;
        return (restored.signum() > 0 ? "₹" + restored.stripTrailingZeros().toPlainString() + " was restored"
                : "₹" + restored.abs().stripTrailingZeros().toPlainString() + " was deducted");
    }
}
