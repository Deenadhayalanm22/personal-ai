package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.StateMutationCommand;
import com.apps.deen_sa.core.mutation.StateMutationEntity;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.mutation.StateMutationRepository;
import com.apps.deen_sa.finance.account.strategy.ValueAdjustmentStrategyResolver;
import com.apps.deen_sa.finance.account.strategy.ValueAdjustmentStrategy;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ValueAdjustmentService {

    private final StateMutationRepository adjustmentRepository;
    private final ValueAdjustmentStrategyResolver strategyResolver;
    private final ValueContainerService valueContainerService;

    public ValueAdjustmentService(
            StateMutationRepository adjustmentRepository,
            ValueAdjustmentStrategyResolver strategyResolver,
            ValueContainerService valueContainerService
    ) {
        this.adjustmentRepository = adjustmentRepository;
        this.strategyResolver = strategyResolver;
        this.valueContainerService = valueContainerService;
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
        ValueAdjustmentStrategy strategy =
                strategyResolver.resolve(container);

        strategy.apply(container, command);

        // 3️⃣ Persist container
        container.setLastActivityAt(Instant.now());
        valueContainerService.UpdateValueContainer(container);
    }
}
