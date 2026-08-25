package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.dto.TaxonomyCandidateDto;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaxonomyCandidateServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TaxonomyCandidateService service =
            new TaxonomyCandidateService(jdbc, new ExpenseTaxonomyRegistry());

    @Test
    void recordsReusableProposalForFallbackClassification() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(12L);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ExpenseDto expense = expense("Other Shopping", "Shopping", "Outdoor & Sports Equipment");
        expense.getTaxonomyCandidate().setItemConcept("Beach mat");
        expense.getTaxonomyCandidate().setConfidence(new BigDecimal("0.88"));

        service.recordIfUseful(expense, 44L);

        verify(jdbc).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void ignoresProposalWhenTransactionHasSpecificClassification() {
        ExpenseDto expense = expense("Household Items", "Shopping", "Outdoor & Sports Equipment");

        service.recordIfUseful(expense, 44L);

        verifyNoInteractions(jdbc);
    }

    @Test
    void ignoresProposalThatAlreadyExistsInCanonicalTaxonomy() {
        ExpenseDto expense = expense("Other Shopping", "Shopping", "Household Items");

        service.recordIfUseful(expense, 44L);

        verifyNoInteractions(jdbc);
    }

    private ExpenseDto expense(String canonicalSubcategory, String proposedCategory, String proposedSubcategory) {
        ExpenseDto value = new ExpenseDto();
        value.setCategory("Shopping"); value.setSubcategory(canonicalSubcategory);
        TaxonomyCandidateDto proposal = new TaxonomyCandidateDto();
        proposal.setCategory(proposedCategory); proposal.setSubcategory(proposedSubcategory);
        value.setTaxonomyCandidate(proposal);
        return value;
    }
}
