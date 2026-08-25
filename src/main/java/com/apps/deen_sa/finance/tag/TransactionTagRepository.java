package com.apps.deen_sa.finance.tag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TransactionTagRepository extends JpaRepository<TransactionTagEntity, TransactionTagId> {
    List<TransactionTagEntity> findAllByTransactionIdIn(Collection<Long> transactionIds);
    List<TransactionTagEntity> findAllByTransactionId(Long transactionId);
    void deleteAllByTransactionId(Long transactionId);
}
