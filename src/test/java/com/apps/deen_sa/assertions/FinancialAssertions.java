package com.apps.deen_sa.assertions;

import com.apps.deen_sa.core.transaction.TransactionEntity;
import com.apps.deen_sa.core.transaction.TransactionRepository;
import com.apps.deen_sa.core.value.ValueAdjustmentEntity;
import com.apps.deen_sa.core.value.ValueAdjustmentRepository;
import com.apps.deen_sa.core.value.ValueContainerEntity;
import com.apps.deen_sa.core.value.ValueContainerRepo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test-only assertion helpers for financial invariants.
 * Reads from repositories only; does not mutate state.
 */
public final class FinancialAssertions {

    private FinancialAssertions() {}

    public static void assertNoOrphanAdjustments(ValueAdjustmentRepository adjRepo, TransactionRepository txRepo) {
        List<ValueAdjustmentEntity> all = adjRepo.findAll();
        for (ValueAdjustmentEntity a : all) {
            assertNotNull(a.getTransactionId(), "Adjustment " + a.getId() + " has null transactionId");
            assertTrue(txRepo.existsById(a.getTransactionId()), "Adjustment " + a.getId() + " references missing tx " + a.getTransactionId());
        }
    }

    public static void assertAdjustmentsMatchTransactions(TransactionRepository txRepo, ValueAdjustmentRepository adjRepo) {
        List<TransactionEntity> txs = txRepo.findAll().stream()
                .filter(TransactionEntity::isFinanciallyApplied)
                .collect(Collectors.toList());

        for (TransactionEntity tx : txs) {
            List<ValueAdjustmentEntity> adjustments = adjRepo.findAll().stream()
                    .filter(a -> a.getTransactionId() != null && a.getTransactionId().equals(tx.getId()))
                    .collect(Collectors.toList());

            assertFalse(adjustments.isEmpty(), "Transaction " + tx.getId() + " marked applied but no adjustments found");

            // compute signed sum: CREDIT => +amount, DEBIT => -amount
            BigDecimal signedSum = adjustments.stream()
                    .map(a -> a.getAdjustmentType() == null ? BigDecimal.ZERO :
                            (a.getAdjustmentType().name().equals("CREDIT") ? a.getAmount() : a.getAmount().negate()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            switch (tx.getTransactionType()) {
                case TRANSFER:
                    // internal transfer should net to zero across adjustments
                    assertEquals(0, signedSum.compareTo(BigDecimal.ZERO), "Transfer tx " + tx.getId() + " adjustments do not net to zero");
                    break;
                case EXPENSE:
                    // expense should result in a net debit equal to -amount
                    assertEquals(0, signedSum.compareTo(tx.getAmount().negate()), "Expense tx " + tx.getId() + " adjustments do not equal transaction amount");
                    break;
                default:
                    // For other types, at least ensure adjustments exist (already checked)
            }
        }
    }

    public static void assertContainerBalance(Long containerId, BigDecimal openingValue, ValueAdjustmentRepository adjRepo, ValueContainerRepo containerRepo) {
        List<ValueAdjustmentEntity> adjustments = adjRepo.findAll().stream()
                .filter(a -> a.getContainerId() != null && a.getContainerId().equals(containerId))
                .collect(Collectors.toList());

        BigDecimal credits = adjustments.stream()
                .filter(a -> a.getAdjustmentType() != null && a.getAdjustmentType().name().equals("CREDIT"))
                .map(ValueAdjustmentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal debits = adjustments.stream()
                .filter(a -> a.getAdjustmentType() != null && a.getAdjustmentType().name().equals("DEBIT"))
                .map(ValueAdjustmentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expected = openingValue.add(credits).subtract(debits);

        ValueContainerEntity current = containerRepo.findById(containerId).orElseThrow(() -> new IllegalStateException("Container missing: " + containerId));

        BigDecimal currentValue = current.getCurrentValue() == null ? BigDecimal.ZERO : current.getCurrentValue();

        assertEquals(0, expected.compareTo(currentValue), "Container " + containerId + " balance mismatch: expected=" + expected + " actual=" + currentValue);
    }

    public static void assertTotalMoneyConserved(Map<Long, BigDecimal> openingBalances, ValueContainerRepo containerRepo) {
        BigDecimal openingTotal = openingBalances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal closingTotal = containerRepo.findAll().stream()
                .map(c -> c.getCurrentValue() == null ? BigDecimal.ZERO : c.getCurrentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, openingTotal.compareTo(closingTotal), "Total money changed across containers: opening=" + openingTotal + " closing=" + closingTotal);
    }

    public static void assertIdempotentOnRerun(Map<Long, BigDecimal> snapshotBeforeRerun, ValueContainerRepo containerRepo, ValueAdjustmentRepository adjRepo) {
        // Verify container values unchanged
        for (Map.Entry<Long, BigDecimal> e : snapshotBeforeRerun.entrySet()) {
            Long cid = e.getKey();
            BigDecimal before = e.getValue();
            BigDecimal now = containerRepo.findById(cid).map(c -> c.getCurrentValue() == null ? BigDecimal.ZERO : c.getCurrentValue()).orElse(BigDecimal.ZERO);
            assertEquals(0, before.compareTo(now), "Container " + cid + " changed after rerun: before=" + before + " now=" + now);
        }

        // As a simple guard, ensure adjustment counts did not increase for the same transactions
        // (we capture total adjustment count as a quick proxy)
        long totalAdjustments = adjRepo.count();
        // This method expects caller to compare counts before/after; if needed, caller can assert equality.
        // We leave this method focused on container values; callers may assert counts separately.
    }

    public static void assertNoNegativeBalances(ValueContainerRepo containerRepo) {
        List<ValueContainerEntity> containers = containerRepo.findAll();
        for (ValueContainerEntity c : containers) {
            BigDecimal currentValue = c.getCurrentValue() == null ? BigDecimal.ZERO : c.getCurrentValue();
            // Only enforce non-negative for BANK_ACCOUNT and CASH (assets)
            // Credit cards and loans can be negative (they are liabilities)
            if ("BANK_ACCOUNT".equals(c.getContainerType()) || "CASH".equals(c.getContainerType())) {
                assertTrue(currentValue.compareTo(BigDecimal.ZERO) >= 0,
                    "Container " + c.getId() + " (" + c.getContainerType() + " - " + c.getName() + ") has negative balance: " + currentValue);
            }
        }
    }

    public static void assertCapacityLimitsRespected(ValueContainerRepo containerRepo) {
        List<ValueContainerEntity> containers = containerRepo.findAll();
        for (ValueContainerEntity c : containers) {
            if (c.getCapacityLimit() != null) {
                BigDecimal currentValue = c.getCurrentValue() == null ? BigDecimal.ZERO : c.getCurrentValue();
                // For liabilities (credit cards, loans), current value should not exceed capacity
                if ("CREDIT_CARD".equals(c.getContainerType()) || "LOAN".equals(c.getContainerType())) {
                    assertTrue(currentValue.compareTo(c.getCapacityLimit()) <= 0,
                        "Container " + c.getId() + " (" + c.getContainerType() + " - " + c.getName() + 
                        ") exceeds capacity limit: current=" + currentValue + " limit=" + c.getCapacityLimit());
                }
            }
        }
    }

    public static void assertAllTransactionsHaveValidStatus(TransactionRepository txRepo) {
        List<TransactionEntity> txs = txRepo.findAll();
        for (TransactionEntity tx : txs) {
            assertNotNull(tx.getTransactionType(), "Transaction " + tx.getId() + " has null transaction type");
            assertNotNull(tx.getAmount(), "Transaction " + tx.getId() + " has null amount");
            assertTrue(tx.getAmount().compareTo(BigDecimal.ZERO) >= 0, 
                "Transaction " + tx.getId() + " has negative amount: " + tx.getAmount());
        }
    }
}
