package com.apps.deen_sa.repository;

import com.apps.deen_sa.entity.InvestmentTransactionEntity;
import com.apps.deen_sa.domain.InvestmentTransactionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDate;
import java.util.Optional;

public interface InvestmentTransactionRepository extends JpaRepository<InvestmentTransactionEntity, Long> {
    List<InvestmentTransactionEntity> findByInvestmentIdOrderByCreatedAtAsc(Long investmentId);
    Optional<InvestmentTransactionEntity> findByInvestmentIdAndTransactionKindAndScheduledMonth(
            Long investmentId, InvestmentTransactionKind transactionKind, LocalDate scheduledMonth);
}
