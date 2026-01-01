package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.finance.account.strategy.CreditSettlementStrategy;
import com.apps.deen_sa.core.mutation.strategy.StateMutationStrategy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class CreditCardStrategy implements StateMutationStrategy, CreditSettlementStrategy {

    @Override
    public boolean supports(StateContainerEntity container) {
        return container.getContainerType().equals("CREDIT_CARD");
    }

    /**
     * Expense on credit card → increases outstanding amount
     */
    @Override
    public void apply(StateContainerEntity container, StateMutationCommand cmd) {

        BigDecimal outstanding = defaultZero(container.getCurrentValue());
        BigDecimal newOutstanding = outstanding.add(cmd.getAmount());

        container.setCurrentValue(newOutstanding);
        evaluateOverLimit(container, newOutstanding);
        container.setLastActivityAt(Instant.now());
    }

    /**
     * Refund / reversal → reduces outstanding amount
     */
    @Override
    public void reverse(StateContainerEntity container, StateMutationCommand cmd) {

        BigDecimal outstanding = defaultZero(container.getCurrentValue());
        BigDecimal newOutstanding = outstanding.subtract(cmd.getAmount());

        if (newOutstanding.signum() < 0) {
            newOutstanding = BigDecimal.ZERO;
        }

        container.setCurrentValue(newOutstanding);
        evaluateOverLimit(container, newOutstanding);
        container.setLastActivityAt(Instant.now());
    }

    /**
     * Payment → reduces outstanding amount
     * Exists only for credit cards
     */
    @Override
    public void applyPayment(StateContainerEntity container, BigDecimal amount) {

        BigDecimal outstanding = defaultZero(container.getCurrentValue());
        BigDecimal newOutstanding = outstanding.subtract(amount);

        if (newOutstanding.signum() < 0) {
            newOutstanding = BigDecimal.ZERO;
        }

        container.setCurrentValue(newOutstanding);
        evaluateOverLimit(container, newOutstanding);
        container.setLastActivityAt(Instant.now());
    }

    private BigDecimal defaultZero(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }

    /**
     * Determines over-limit state without blocking persistence
     */
    private void evaluateOverLimit(StateContainerEntity container,
                                   BigDecimal outstanding) {

        BigDecimal limit = container.getCapacityLimit();

        if (limit == null) {
            container.setOverLimit(false);
            container.setOverLimitAmount(BigDecimal.ZERO);
            return;
        }

        if (outstanding.compareTo(limit) > 0) {
            container.setOverLimit(true);
            container.setOverLimitAmount(outstanding.subtract(limit));
        } else {
            container.setOverLimit(false);
            container.setOverLimitAmount(BigDecimal.ZERO);
        }
    }
}
