package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionEnrichmentServiceTest {
    private final TransactionEnrichmentService service = new TransactionEnrichmentService();

    @Test
    void mergesSeveralExplicitDetailsAndPreservesEstablishedCoreFacts() {
        ExpenseDto candidate = candidate();
        ExpenseDto proposal = new ExpenseDto();
        proposal.setAmount(new BigDecimal("9999"));
        proposal.setMerchantName("Invented replacement");
        proposal.setSourceAccount("HDFC");
        proposal.setDetails(Map.of("beneficiary", "Sachin", "occasion", "Birthday", "plannedStatus", "planned"));

        service.merge(candidate, new TransactionEnrichment(proposal, EnrichmentSource.EXPLICIT));

        assertThat(candidate.getAmount()).isEqualByComparingTo("2500");
        assertThat(candidate.getMerchantName()).isEqualTo("Trends");
        assertThat(candidate.getCategory()).isEqualTo("Shopping");
        assertThat(candidate.getDetails()).containsEntry("beneficiary", "Sachin")
                .containsEntry("occasion", "Birthday").containsEntry("plannedStatus", "PLANNED");
        assertThat(candidate.getSourceAccount()).isEqualTo("HDFC");
    }

    @Test
    void partialEnrichmentDoesNotInventAbsentContext() {
        ExpenseDto candidate = candidate();
        ExpenseDto proposal = new ExpenseDto();
        proposal.setDetails(Map.of("beneficiary", "Sachin"));

        service.merge(candidate, new TransactionEnrichment(proposal, EnrichmentSource.EXPLICIT));

        assertThat(candidate.getDetails()).containsOnly(Map.entry("beneficiary", "Sachin"));
    }

    @Test
    void unknownProvenanceCannotChangeCandidate() {
        ExpenseDto candidate = candidate();
        ExpenseDto proposal = new ExpenseDto();
        proposal.setDetails(Map.of("occasion", "Birthday"));

        service.merge(candidate, new TransactionEnrichment(proposal, EnrichmentSource.UNKNOWN));

        assertThat(candidate.getDetails()).isNull();
    }

    @Test
    void rejectsUnsupportedOptionalAttributes() {
        ExpenseDto proposal = new ExpenseDto();
        proposal.setDetails(Map.of("secretModelGuess", "value"));
        assertThatThrownBy(() -> service.merge(candidate(),
                new TransactionEnrichment(proposal, EnrichmentSource.EXPLICIT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ExpenseDto candidate() {
        ExpenseDto dto = new ExpenseDto();
        dto.setAmount(new BigDecimal("2500")); dto.setMerchantName("Trends");
        dto.setTransactionDate(LocalDate.of(2026, 8, 31));
        dto.setCategory("Shopping"); dto.setSubcategory("Clothing");
        return dto;
    }
}
