package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

/**
 * FIN-019 — Private income outlook. See docs/jira/personal-expense/FIN-EPIC-005-planning.md.
 */
@Service
public class WebIncomeService {
    private static final Set<String> RANGES = Set.of("UNDER_25000", "FROM_25000_TO_50000", "FROM_50000_TO_100000", "FROM_100000_TO_200000", "OVER_200000");
    private static final Set<String> SALARY_FREQUENCIES = Set.of("MONTHLY", "TWICE_MONTHLY", "WEEKLY", "IRREGULAR");
    private final UserIncomeProfileRepository profiles;

    public WebIncomeService(UserIncomeProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public OutlookResponse outlook(AppUserEntity u) {
        return new OutlookResponse(profiles.findById(u.getId()).map(ProfileResponse::from).orElse(null));
    }

    @Transactional
    public ProfileResponse saveProfile(AppUserEntity u, ProfileRequest r) {
        if (r == null || !Set.of("RANGE", "EXACT", "SKIPPED").contains(r.salaryVisibility()))
            throw invalid("Choose how you would like to share your salary");
        if (!SALARY_FREQUENCIES.contains(r.salaryFrequency())) throw invalid("Choose a salary frequency");
        if ("RANGE".equals(r.salaryVisibility()) && !RANGES.contains(r.salaryRange()))
            throw invalid("Choose a salary range");
        if ("EXACT".equals(r.salaryVisibility()) && positive(r.exactMonthlySalary(), "exactMonthlySalary") == null)
            throw invalid("Enter a positive salary");
        UserIncomeProfileEntity p = profiles.findById(u.getId()).orElseGet(UserIncomeProfileEntity::new);
        // Authentication supplies a detached user in web requests; the shared primary key is all this profile needs.
        p.setUserId(u.getId());
        p.setSalaryVisibility(r.salaryVisibility());
        p.setSalaryRange("RANGE".equals(r.salaryVisibility()) ? r.salaryRange() : null);
        p.setExactMonthlySalary("EXACT".equals(r.salaryVisibility()) ? positive(r.exactMonthlySalary(), "exactMonthlySalary") : null);
        p.setSalaryFrequency(r.salaryFrequency());
        p.setUpdatedAt(Instant.now());
        return ProfileResponse.from(profiles.save(p));
    }

    private BigDecimal positive(BigDecimal v, String f) {
        return v == null || v.signum() <= 0 ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private WebApiException invalid(String m) {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_INCOME_OUTLOOK", m);
    }

    public record ProfileRequest(String salaryVisibility, String salaryRange, BigDecimal exactMonthlySalary,
                                 String salaryFrequency) {
    }

    public record ProfileResponse(String salaryVisibility, String salaryRange, BigDecimal exactMonthlySalary,
                                  String salaryFrequency) {
        static ProfileResponse from(UserIncomeProfileEntity e) {
            return new ProfileResponse(e.getSalaryVisibility(), e.getSalaryRange(), e.getExactMonthlySalary(), e.getSalaryFrequency());
        }
    }

    public record OutlookResponse(ProfileResponse salary) {
    }
}
