package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.repository.UserIncomeProfileRepository;
import org.springframework.stereotype.Component;

/** Voluntary salary data is context only: never a transaction, balance, or evidence row. */
@Component
public class SalaryStoryContextContributor implements StoryContextContributor {
    private final UserIncomeProfileRepository profiles;
    public SalaryStoryContextContributor(UserIncomeProfileRepository profiles) { this.profiles = profiles; }
    @Override public boolean supports(MoneyStoryType type) { return type == MoneyStoryType.MONTHLY_COMMITMENT; }
    @Override public void contribute(StoryEnrichmentRequest request, StoryContext context) {
        profiles.findById(request.user().getId()).ifPresent(profile -> {
            context.put("salary.visibility", profile.getSalaryVisibility());
            context.put("salary.frequency", profile.getSalaryFrequency());
            if ("RANGE".equals(profile.getSalaryVisibility())) context.put("salary.range", profile.getSalaryRange());
            if ("EXACT".equals(profile.getSalaryVisibility())) context.put("salary.exactMonthly", profile.getExactMonthlySalary());
        });
    }
}
