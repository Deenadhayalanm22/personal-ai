package com.apps.deen_sa.entity;
import com.apps.deen_sa.domain.LoanEmiOccurrenceStatus;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
import java.math.BigDecimal; import java.time.*;
@Entity @Table(name="loan_emi_occurrence") @Getter @Setter
public class LoanEmiOccurrenceEntity { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="loan_id") private UserLoanEntity loan; @Column(name="due_month") private LocalDate dueMonth; @Column(name="due_date") private LocalDate dueDate; @Enumerated(EnumType.STRING) private LoanEmiOccurrenceStatus status; private BigDecimal paidAmount; private LocalDate paidAt; private Instant createdAt=Instant.now(); private Instant updatedAt=Instant.now(); }
