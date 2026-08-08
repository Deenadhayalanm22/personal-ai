package com.apps.deen_sa.finance.legacy.mutation.strategy;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;

public interface StateMutationStrategy {

    boolean supports(StateContainerEntity container);

    void apply(StateContainerEntity container, StateMutationCommand command);

    void reverse(StateContainerEntity container, StateMutationCommand command);
}
