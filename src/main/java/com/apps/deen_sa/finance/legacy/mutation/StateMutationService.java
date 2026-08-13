package com.apps.deen_sa.finance.legacy.mutation;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationRepository;
import com.apps.deen_sa.finance.legacy.mutation.strategy.StateMutationStrategyResolver;
import com.apps.deen_sa.finance.legacy.mutation.strategy.StateMutationStrategy;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
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

    @Transactional
    public void reverse(StateContainerEntity container, StateMutationCommand original, String reason) {
        StateMutationEntity audit = new StateMutationEntity();
        audit.setTransactionId(original.getReferenceTxId());
        audit.setContainerId(container.getId());
        audit.setAdjustmentType(original.getType());
        audit.setAmount(original.getAmount().negate());
        audit.setReason(reason);
        audit.setOccurredAt(Instant.now());
        audit.setCreatedAt(Instant.now());
        adjustmentRepository.save(audit);

        strategyResolver.resolve(container).reverse(container, original);
        container.setLastActivityAt(Instant.now());
        stateContainerService.UpdateValueContainer(container);
    }
}
