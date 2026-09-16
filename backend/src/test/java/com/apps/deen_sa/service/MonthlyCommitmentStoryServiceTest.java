package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyCommitmentStoryServiceTest {
    @Test
    void totalsOnlyCommitmentsActiveInTheCurrentMonth() {
        MonthlyFinancialSnapshotService snapshots = mock(MonthlyFinancialSnapshotService.class);
        MoneyStoryCopyGenerator copy = mock(MoneyStoryCopyGenerator.class);
        AppUserEntity user = new AppUserEntity();
        user.setId(7L); user.setCurrency("INR"); user.setTimezone("Asia/Kolkata");
        var october = new MonthlyFinancialSnapshotService.MonthlySnapshot("2026-10", "INR", 3,
                new BigDecimal("46000"), List.of(
                new MonthlyFinancialSnapshotService.Bucket("DEBT_REPAYMENTS", "Debt repayments", new BigDecimal("26000"), List.of(
                        new MonthlyFinancialSnapshotService.Source("LOAN", "1", "Home loan", new BigDecimal("26000"), null, "Loan EMI", "Bank", 24, "2028-09", "2028-10"))),
                new MonthlyFinancialSnapshotService.Bucket("PLANNED_INVESTING", "Planned investing", new BigDecimal("20000"), List.of(
                        new MonthlyFinancialSnapshotService.Source("MUTUAL_FUND_SIP", "2", "Fund", new BigDecimal("20000"), null, "Mutual fund SIP", "Active SIP", null, null, null)))));
        var november = new MonthlyFinancialSnapshotService.MonthlySnapshot("2026-11", "INR", 3,
                new BigDecimal("46000"), october.commitmentBuckets());
        when(snapshots.current(user)).thenReturn(october);
        when(snapshots.next(user)).thenReturn(november);

        when(copy.generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        var story = new MonthlyCommitmentStoryService(snapshots, copy,
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneId.of("Asia/Kolkata"))).currentFor(user);

        assertThat(story.storyType()).isEqualTo("MONTHLY_COMMITMENT");
        assertThat(story.storyId()).isEqualTo("monthly-commitment");
        assertThat(story.cardFace().displayValue()).contains("46,000");
        assertThat(story.cards().getFirst().components()).extracting(component -> component.label())
                .containsExactly("Debt repayments", "Planned investing");
        assertThat(story.cards().getFirst().layout()).isEqualTo("COMMITMENT");
        assertThat(story.cards()).hasSize(2);
        assertThat(story.cards().get(1).cardId()).isEqualTo("next-commitment");
        assertThat(story.cards().get(1).eyebrow()).isEqualTo("NEXT MONTH · READY");
        assertThat(story.cards().getFirst().body()).contains("free from October 2028");
        var currentEvidence = story.evidence().byCard().get("commitment");
        assertThat(currentEvidence.transactions()).hasSize(2);
        assertThat(currentEvidence.transactions()).extracting(row -> row.categoryLabel())
                .containsExactly("Loan EMI · 👾 Long-game member · ends September 2028",
                        "Mutual fund SIP · 🌱 Future-you contribution");
        assertThat(story.cards().getFirst().actions()).extracting(action -> action.type()).containsExactly("OPEN_EVIDENCE");
        assertThat(story.evidence().byCard()).containsKeys("commitment", "next-commitment");
    }
}
