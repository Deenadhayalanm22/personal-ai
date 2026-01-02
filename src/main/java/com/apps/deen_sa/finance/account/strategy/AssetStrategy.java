package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.core.mutation.strategy.StateMutationStrategy;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.dto.StateMutationCommand;
import org.springframework.stereotype.Component;

/**
 * Mutation strategy for ASSET containers.
 * 
 * ASSET containers track quantity, not monetary value.
 * CREDIT mutations increase quantity (BUY transactions).
 * DEBIT mutations decrease quantity (SELL transactions - future).
 */
@Component
public class AssetStrategy implements StateMutationStrategy {

    @Override
    public boolean supports(StateContainerEntity container) {
        return container.getContainerType() != null && 
               container.getContainerType().equals("ASSET");
    }

    @Override
    public void apply(StateContainerEntity container, StateMutationCommand cmd) {
        // For ASSET containers, we track quantity not monetary value
        // CREDIT = increase quantity (BUY)
        // DEBIT = decrease quantity (SELL - future)
        
        switch (cmd.getType()) {
            case CREDIT -> {
                // BUY: increase asset quantity
                container.setCurrentValue(
                        container.getCurrentValue().add(cmd.getAmount())
                );
            }
            case DEBIT -> {
                // SELL: decrease asset quantity (future implementation)
                container.setCurrentValue(
                        container.getCurrentValue().subtract(cmd.getAmount())
                );
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported mutation type for ASSET: " + cmd.getType()
            );
        }
        
        // For assets, available value equals current value
        container.setAvailableValue(container.getCurrentValue());
    }

    @Override
    public void reverse(StateContainerEntity container, StateMutationCommand cmd) {
        // Reverse the mutation
        switch (cmd.getType()) {
            case CREDIT -> {
                // Reverse BUY: decrease quantity
                container.setCurrentValue(
                        container.getCurrentValue().subtract(cmd.getAmount())
                );
            }
            case DEBIT -> {
                // Reverse SELL: increase quantity
                container.setCurrentValue(
                        container.getCurrentValue().add(cmd.getAmount())
                );
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported mutation type for ASSET: " + cmd.getType()
            );
        }
        
        container.setAvailableValue(container.getCurrentValue());
    }
}
