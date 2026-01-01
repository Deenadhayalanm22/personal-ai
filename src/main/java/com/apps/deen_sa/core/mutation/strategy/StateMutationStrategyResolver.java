package com.apps.deen_sa.core.mutation.strategy;

import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.mutation.strategy.StateMutationStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StateMutationStrategyResolver {

    private final List<StateMutationStrategy> strategies;

    public StateMutationStrategyResolver(List<StateMutationStrategy> strategies) {
        this.strategies = strategies;
    }

    public StateMutationStrategy resolve(StateContainerEntity container) {
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
