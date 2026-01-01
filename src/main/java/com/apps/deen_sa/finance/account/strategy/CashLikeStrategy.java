package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.mutation.strategy.StateMutationStrategy;
import org.springframework.stereotype.Component;

@Component
public class CashLikeStrategy implements StateMutationStrategy {

    @Override
    public boolean supports(StateContainerEntity container) {
        return container.getContainerType().equals("CASH")
                || container.getContainerType().equals("BANK_ACCOUNT")
                || container.getContainerType().equals("WALLET");
    }

    @Override
    public void apply(StateContainerEntity container, StateMutationCommand cmd) {
        switch (cmd.getType()) {
            case DEBIT, PAYMENT -> {
                // DEBIT/PAYMENT: money leaves (expenses, transfers out, payments)
                container.setCurrentValue(
                        container.getCurrentValue().subtract(cmd.getAmount())
                );
            }
            case CREDIT -> {
                // CREDIT: money arrives (income, transfers in)
                container.setCurrentValue(
                        container.getCurrentValue().add(cmd.getAmount())
                );
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported mutation type for cash-like container: " + cmd.getType()
            );
        }
        container.setAvailableValue(container.getCurrentValue());
    }

    @Override
    public void reverse(StateContainerEntity container, StateMutationCommand cmd) {
        switch (cmd.getType()) {
            case DEBIT, PAYMENT -> {
                // Reverse DEBIT/PAYMENT: add money back
                container.setCurrentValue(
                        container.getCurrentValue().add(cmd.getAmount())
                );
            }
            case CREDIT -> {
                // Reverse CREDIT: subtract money
                container.setCurrentValue(
                        container.getCurrentValue().subtract(cmd.getAmount())
                );
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported mutation type for cash-like container: " + cmd.getType()
            );
        }
        container.setAvailableValue(container.getCurrentValue());
    }
}
