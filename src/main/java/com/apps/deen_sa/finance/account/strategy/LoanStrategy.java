package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.core.state.StateContainerEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class LoanStrategy implements ValueAdjustmentStrategy, CreditSettlementStrategy {

    @Override
    public boolean supports(StateContainerEntity container) {
        return container.getContainerType().equals("LOAN");
    }

    /**
     * When money is borrowed or loan amount increases (rarely used).
     * Increases the outstanding loan amount.
     */
    @Override
    public void apply(StateContainerEntity container, StateMutationCommand cmd) {
        BigDecimal outstanding = defaultZero(container.getCurrentValue());
        BigDecimal newOutstanding = outstanding.add(cmd.getAmount());

        container.setCurrentValue(newOutstanding);
        container.setLastActivityAt(Instant.now());
    }

    /**
     * Refund or correction - reduces outstanding loan amount.
     */
    @Override
    public void reverse(StateContainerEntity container, StateMutationCommand cmd) {
        BigDecimal outstanding = defaultZero(container.getCurrentValue());
        BigDecimal newOutstanding = outstanding.subtract(cmd.getAmount());

        if (newOutstanding.signum() < 0) {
            newOutstanding = BigDecimal.ZERO;
        }

        container.setCurrentValue(newOutstanding);
        container.setLastActivityAt(Instant.now());
    }

    /**
     * Loan payment → reduces outstanding amount.
     * This is the primary method used when user pays loan EMI.
     */
    @Override
    public void applyPayment(StateContainerEntity container, BigDecimal amount) {
        BigDecimal outstanding = defaultZero(container.getCurrentValue());
        BigDecimal newOutstanding = outstanding.subtract(amount);

        if (newOutstanding.signum() < 0) {
            newOutstanding = BigDecimal.ZERO;
        }

        container.setCurrentValue(newOutstanding);
        container.setLastActivityAt(Instant.now());
    }

    private BigDecimal defaultZero(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }
}
