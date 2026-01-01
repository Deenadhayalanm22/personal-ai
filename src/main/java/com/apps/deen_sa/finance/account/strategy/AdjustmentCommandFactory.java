package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.mutation.MutationTypeEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class AdjustmentCommandFactory {

    public StateMutationCommand forExpense(StateChangeEntity tx) {

        return new StateMutationCommand(
                tx.getAmount(),
                MutationTypeEnum.DEBIT,
                "EXPENSE",
                tx.getId(),
                Instant.now()
        );
    }

    public StateMutationCommand forExpenseReversal(StateChangeEntity tx) {

        return new StateMutationCommand(
                tx.getAmount(),
                MutationTypeEnum.CREDIT,
                "EXPENSE_REVERSAL",
                tx.getId(),
                Instant.now()
        );
    }

    /**
     * Create DEBIT adjustment for source container in a transfer.
     * Used when money leaves the source container (e.g., bank account).
     */
    public StateMutationCommand forTransferDebit(StateChangeEntity tx, String reason) {
        return new StateMutationCommand(
                tx.getAmount(),
                MutationTypeEnum.DEBIT,
                reason,
                tx.getId(),
                tx.getTimestamp() != null ? tx.getTimestamp() : Instant.now()
        );
    }

    /**
     * Create CREDIT adjustment for target container in a transfer.
     * Used when money enters the target container (e.g., credit card payment).
     */
    public StateMutationCommand forTransferCredit(StateChangeEntity tx, String reason) {
        return new StateMutationCommand(
                tx.getAmount(),
                MutationTypeEnum.CREDIT,
                reason,
                tx.getId(),
                tx.getTimestamp() != null ? tx.getTimestamp() : Instant.now()
        );
    }
}
