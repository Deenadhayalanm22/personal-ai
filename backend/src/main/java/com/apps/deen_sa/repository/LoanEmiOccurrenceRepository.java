package com.apps.deen_sa.repository;
import com.apps.deen_sa.entity.LoanEmiOccurrenceEntity; import java.time.LocalDate; import java.util.List; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface LoanEmiOccurrenceRepository extends JpaRepository<LoanEmiOccurrenceEntity,Long> { Optional<LoanEmiOccurrenceEntity> findByLoanIdAndDueMonth(Long loanId, LocalDate dueMonth); List<LoanEmiOccurrenceEntity> findByLoanIdOrderByDueMonthAsc(Long loanId); }
