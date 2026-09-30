package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentContextType;
import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;

/** Qualifies salary lenses independently from all other future commitment insight modules. */
@Component
public class CommitmentSalaryInsightRule implements CommitmentInsightRule {
    @Override public boolean supports(CommitmentContextType type) { return type == CommitmentContextType.MONTHLY_COMMITMENT; }
    @Override public Optional<CommitmentInsight> qualify(CommitmentEnrichmentRequest request, CommitmentContext context) {
        String visibility = context.fact("salary.visibility", String.class).orElse(null);
        if ("RANGE".equals(visibility)) return context.fact("salary.range", String.class)
                .map(range -> new CommitmentInsight("COMMITMENT_INCOME_RANGE", Map.of("range", range)));
        if ("EXACT".equals(visibility)) {
            Optional<BigDecimal> salary = context.fact("salary.exactMonthly", BigDecimal.class);
            Optional<BigDecimal> commitment = context.fact("commitment.total", BigDecimal.class);
            if (salary.isPresent() && salary.get().signum() > 0 && commitment.isPresent()) {
                BigDecimal percent = commitment.get().multiply(BigDecimal.valueOf(100)).divide(salary.get(), 1, RoundingMode.HALF_UP);
                // This stays inside story composition. The salary itself is never serialized in the story response.
                return Optional.of(new CommitmentInsight("COMMITMENT_INCOME_EXACT", Map.of("percent", percent, "monthlySalary", salary.get())));
            }
        }
        return Optional.empty();
    }
}
