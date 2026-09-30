package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.CommitmentContextType;
import com.apps.deen_sa.repository.UserIncomeProfileRepository;
import org.springframework.stereotype.Component;

/** Voluntary salary data is context only: never a transaction, balance, or evidence row. */
@Component
public class SalaryCommitmentContextContributor implements CommitmentContextContributor {
    private final UserIncomeProfileRepository profiles;
    public SalaryCommitmentContextContributor(UserIncomeProfileRepository profiles) { this.profiles = profiles; }
    @Override public boolean supports(CommitmentContextType type) { return type == CommitmentContextType.MONTHLY_COMMITMENT; }
    @Override public void contribute(CommitmentEnrichmentRequest request, CommitmentContext context) {
        profiles.findById(request.user().getId()).ifPresent(profile -> {
            context.put("salary.visibility", profile.getSalaryVisibility());
            context.put("salary.frequency", profile.getSalaryFrequency());
            if ("RANGE".equals(profile.getSalaryVisibility())) context.put("salary.range", profile.getSalaryRange());
            if ("EXACT".equals(profile.getSalaryVisibility())) context.put("salary.exactMonthly", profile.getExactMonthlySalary());
        });
    }
}
