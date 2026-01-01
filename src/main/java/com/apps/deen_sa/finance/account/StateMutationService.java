package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.core.mutation.StateMutationEntity;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.mutation.StateMutationRepository;
import com.apps.deen_sa.finance.account.strategy.StateMutationStrategyResolver;
import com.apps.deen_sa.finance.account.strategy.StateMutationStrategy;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class StateMutationService {

    private final StateMutationRepository adjustmentRepository;
    private final StateMutationStrategyResolver strategyResolver;
    private final StateContainerService stateContainerService;

    public StateMutationService(
            StateMutationRepository adjustmentRepository,
            StateMutationStrategyResolver strategyResolver,
            StateContainerService stateContainerService
    ) {
        this.adjustmentRepository = adjustmentRepository;
        this.strategyResolver = strategyResolver;
        this.stateContainerService = stateContainerService;
    }

    @Transactional
    public void apply(StateContainerEntity container,
                      StateMutationCommand command) {

        // 1️⃣ Persist audit record
        StateMutationEntity audit = new StateMutationEntity();
        audit.setTransactionId(command.getReferenceTxId());
        audit.setContainerId(container.getId());
        audit.setAdjustmentType(command.getType());
        audit.setAmount(command.getAmount());
        audit.setReason(command.getReason());
        audit.setOccurredAt(command.getOccurredAt());
        audit.setCreatedAt(Instant.now());

        adjustmentRepository.save(audit);

        // 2️⃣ Apply strategy
        StateMutationStrategy strategy =
                strategyResolver.resolve(container);

        strategy.apply(container, command);

        // 3️⃣ Persist container
        container.setLastActivityAt(Instant.now());
        stateContainerService.UpdateValueContainer(container);
    }
}
