package com.apps.deen_sa.finance.expense.correction;

import com.apps.deen_sa.core.ledger.CoreEventEntity;
import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.extension.api.ExecutionPlan;
import com.apps.deen_sa.extension.api.MovementPlan;
import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.legacy.mutation.MutationTypeEnum;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationEntity;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationRepository;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationService;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.persistence.FinanceAnalyticsRepository;
import com.apps.deen_sa.finance.persistence.FinanceExpenseProjectionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
class ExpenseCorrectionService {
    private final StateChangeRepository transactions;
    private final StateMutationRepository mutationRecords;
    private final StateMutationService mutations;
    private final StateContainerRepository containers;
    private final FinanceAnalyticsRepository projections;
    private final GenericLedgerService ledger;

    ExpenseCorrectionService(StateChangeRepository transactions, StateMutationRepository mutationRecords,
                             StateMutationService mutations, StateContainerRepository containers,
                             FinanceAnalyticsRepository projections, GenericLedgerService ledger) {
        this.transactions = transactions;
        this.mutationRecords = mutationRecords;
        this.mutations = mutations;
        this.containers = containers;
        this.projections = projections;
        this.ledger = ledger;
    }

    @Transactional
    CorrectionOutcome voidExpense(Long userId, Long transactionId) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId);
        Optional<FinanceExpenseProjectionEntity> originalProjection = projectionFor(original);
        reverseLegacyImpact(original);
        original.setRecordStatus(ExpenseRecordStatus.VOIDED);
        original.setCorrectedAt(Instant.now());
        original.setCorrectionReason("USER_DELETED");
        transactions.save(original);
        originalProjection.ifPresent(value -> {
            value.setActive(false);
            projections.save(value);
        });
        commitCorrection(userId, original, null);
        return new CorrectionOutcome(original, null, balanceImpact(original.getAmount(), BigDecimal.ZERO));
    }

    @Transactional
    CorrectionOutcome editExpense(Long userId, Long transactionId, CorrectionField field, Object value) {
        StateChangeEntity original = activeOwnedExpense(userId, transactionId);
        Optional<FinanceExpenseProjectionEntity> originalProjection = projectionFor(original);
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

        originalProjection.ifPresent(value1 -> {
            value1.setActive(false);
            projections.save(value1);
        });
        CoreEventEntity correction = commitCorrection(userId, original, replacement);
        FinanceExpenseProjectionEntity projection = new FinanceExpenseProjectionEntity();
        projection.setCoreEventId(correction.getId());
        projection.setLegacyTransactionId(replacement.getId());
        projection.setTenantId(userId);
        projection.setUserId(userId.toString());
        projection.setAmount(replacement.getAmount());
        projection.setCategory(replacement.getCategory());
        projection.setSubcategory(replacement.getSubcategory());
        projection.setSourceAccount(accountName(replacement.getSourceContainerId()));
        projection.setOccurredAt(replacement.getTimestamp());
        projection.setActive(true);
        projections.save(projection);
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

    private Optional<FinanceExpenseProjectionEntity> projectionFor(StateChangeEntity expense) {
        Optional<FinanceExpenseProjectionEntity> linked = projections.findByLegacyTransactionId(expense.getId());
        if (linked.isPresent() || expense.getRawText() == null) return linked;
        List<FinanceExpenseProjectionEntity> candidates = projections.findLegacyCandidates(
                expense.getUserId(), expense.getAmount(), expense.getRawText());
        if (candidates.size() > 1)
            throw new IllegalStateException("This older expense cannot be corrected safely because its analytics record is ambiguous.");
        return candidates.stream().findFirst();
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

    private CoreEventEntity commitCorrection(Long userId, StateChangeEntity original, StateChangeEntity replacement) {
        List<MovementPlan> movements = new ArrayList<>();
        if (original.isFinanciallyApplied()) {
            movements.addAll(expenseMovements(original, true));
            if (replacement != null) movements.addAll(expenseMovements(replacement, false));
        }
        Map<String, Object> facts = new HashMap<>();
        facts.put("originalTransactionId", original.getId());
        facts.put("action", replacement == null ? "VOID" : "EDIT");
        if (replacement != null) facts.put("replacementTransactionId", replacement.getId());
        String key = "expense-correction:" + original.getId() + ":" + (replacement == null ? "void" : replacement.getId());
        return ledger.commit(new ExecutionPlan(userId, "personal-finance", replacement == null ? "EXPENSE_VOIDED" : "EXPENSE_CORRECTED",
                "1.0.0", Instant.now(), "user:" + userId, Map.copyOf(facts), Map.of(), "finance-correction-v1",
                key, "transaction:" + original.getId(), movements, List.of()));
    }

    private List<MovementPlan> expenseMovements(StateChangeEntity expense, boolean reverse) {
        BigDecimal amount = reverse ? expense.getAmount() : expense.getAmount().negate();
        return List.of(
                new MovementPlan("INR", accountContainer(expense.getSourceContainerId()), amount, "INR"),
                new MovementPlan("INR", "expense:" + slug(expense.getCategory()), amount.negate(), "INR"));
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

    private String accountContainer(Long id) { return id == null ? "unallocated-source" : "account:" + slug(accountName(id)); }
    private String accountName(Long id) { return id == null ? "Unallocated" : containers.findById(id).map(StateContainerEntity::getName).orElse("Unknown"); }
    private String slug(String value) { return value == null ? "uncategorized" : value.toLowerCase().trim().replaceAll("[^\\p{L}0-9]+", "-"); }
    private String balanceImpact(BigDecimal oldAmount, BigDecimal newAmount) {
        BigDecimal restored = oldAmount.subtract(newAmount);
        if (restored.signum() == 0) return null;
        return (restored.signum() > 0 ? "₹" + restored.stripTrailingZeros().toPlainString() + " was restored"
                : "₹" + restored.abs().stripTrailingZeros().toPlainString() + " was deducted");
    }
}
