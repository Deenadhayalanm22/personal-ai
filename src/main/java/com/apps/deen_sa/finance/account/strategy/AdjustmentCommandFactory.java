package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.dto.AdjustmentCommand;
import com.apps.deen_sa.core.transaction.TransactionEntity;
import com.apps.deen_sa.core.value.AdjustmentTypeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class AdjustmentCommandFactory {

    public AdjustmentCommand forExpense(TransactionEntity tx) {

        return new AdjustmentCommand(
                tx.getAmount(),
                AdjustmentTypeEnum.DEBIT,
                "EXPENSE",
                tx.getId(),
                Instant.now()
        );
    }

    public AdjustmentCommand forExpenseReversal(TransactionEntity tx) {

        return new AdjustmentCommand(
                tx.getAmount(),
                AdjustmentTypeEnum.CREDIT,
                "EXPENSE_REVERSAL",
                tx.getId(),
                Instant.now()
        );
    }

    /**
     * Create DEBIT adjustment for source container in a transfer.
     * Used when money leaves the source container (e.g., bank account).
     */
    public AdjustmentCommand forTransferDebit(TransactionEntity tx, String reason) {
        return new AdjustmentCommand(
                tx.getAmount(),
                AdjustmentTypeEnum.DEBIT,
                reason,
                tx.getId(),
                tx.getTimestamp() != null ? tx.getTimestamp() : Instant.now()
        );
    }

    /**
     * Create CREDIT adjustment for target container in a transfer.
     * Used when money enters the target container (e.g., credit card payment).
     */
    public AdjustmentCommand forTransferCredit(TransactionEntity tx, String reason) {
        return new AdjustmentCommand(
                tx.getAmount(),
                AdjustmentTypeEnum.CREDIT,
                reason,
                tx.getId(),
                tx.getTimestamp() != null ? tx.getTimestamp() : Instant.now()
        );
    }
}
