package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.finance.account.strategy.ValueAdjustmentStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ValueAdjustmentStrategyResolver {

    private final List<ValueAdjustmentStrategy> strategies;

    public ValueAdjustmentStrategyResolver(List<ValueAdjustmentStrategy> strategies) {
        this.strategies = strategies;
    }

    public ValueAdjustmentStrategy resolve(StateContainerEntity container) {
        return strategies.stream()
                .filter(s -> s.supports(container))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No strategy for container type: "
                                        + container.getContainerType()
                        )
                );
    }
}
