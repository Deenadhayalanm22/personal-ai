package com.apps.deen_sa.finance.legacy.mutation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StateMutationRepository extends JpaRepository<StateMutationEntity, Long> {
    List<StateMutationEntity> findByTransactionIdOrderByIdAsc(Long transactionId);
}
