package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import org.junit.jupiter.api.Test;
import java.time.YearMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyCommitmentPresentationServiceTest {
    @Test
    void monthlyReadReturnsOnlyLiveCommitmentWithoutSnapshotDependencies() {
        MonthlyCommitmentCardService commitment = mock(MonthlyCommitmentCardService.class);
        AppUserEntity user = new AppUserEntity();
        user.setCurrency("INR");
        user.setTimezone("Asia/Kolkata");
        MonthlyCommitmentPresentationService.CommitmentPresentation card = new MonthlyCommitmentPresentationService.CommitmentPresentation(
                "monthly-commitment", "MONTHLY_COMMITMENT", 1, null, new MonthlyCommitmentPresentationService.PeriodDto("MONTH", java.time.LocalDate.of(2026, 9, 1), java.time.LocalDate.of(2026, 9, 30), "September 2026"), null,
                java.util.List.of(), null);
        when(commitment.currentFor(user)).thenReturn(card);

        var response = new MonthlyCommitmentPresentationService(commitment).monthlyForWeb(user, YearMonth.of(2026, 9));

        assertThat(response.month()).isEqualTo("2026-09");
        assertThat(response.commitment()).isSameAs(card);
        assertThat(new MonthlyCommitmentPresentationService(commitment).monthlyForWeb(user, YearMonth.of(2026, 8)).commitment()).isNull();
        verify(commitment, org.mockito.Mockito.times(2)).currentFor(user);
    }
}
