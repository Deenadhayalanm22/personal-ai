package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.support.SeptemberStoryFixture;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MoneyStoryObservationTest {
    private final MoneyStoryObservationFactory factory = new MoneyStoryObservationFactory();
    private List<MoneyStoryCandidate> candidates() throws Exception {
        return factory.create(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 11), SeptemberStoryFixture.expenses());
    }
    private MoneyStoryCandidate food() throws Exception {
        return candidates().stream().filter(c -> c.type() == MoneyStoryType.CATEGORY_SPENDING_GROWTH && "Food & Dining".equals(c.category())).findFirst().orElseThrow();
    }
    private MoneyStoryCandidate dayTen() throws Exception {
        return candidates().stream().filter(c -> c.type() == MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY && c.periodStart().equals(LocalDate.of(2026, 9, 10))).findFirst().orElseThrow();
    }
    @Test void explainsThePurchaseBehindSeptemberTenthInsteadOfRepeatingTheDailyTotal() throws Exception {
        var facts = dayTen().observation();
        assertThat(facts.kind()).isEqualTo(MoneyStoryObservation.Kind.CONTRIBUTOR);
        assertThat(facts.focusAmount()).isEqualByComparingTo("5445");
        assertThat(facts.total()).isEqualByComparingTo("6107");
        assertThat(facts.otherAmount()).isEqualByComparingTo("662");
        assertThat(facts.sharePercent()).isEqualByComparingTo("89.16");
        assertThat(facts.focusTransactionIds()).containsExactly(27L);
        assertThat(facts.subject()).contains("Household Items");
    }
    @Test void foodBreakdownIncludesExpensesWhoseSpendingNatureIsMissing() throws Exception {
        var facts = food().observation();
        assertThat(facts.kind()).isEqualTo(MoneyStoryObservation.Kind.BREAKDOWN);
        assertThat(facts.focusAmount()).isEqualByComparingTo("13476");
        assertThat(facts.total()).isEqualByComparingTo("16203");
        assertThat(facts.otherAmount()).isEqualByComparingTo("2727");
        assertThat(facts.incompleteClassificationCount()).isEqualTo(2);
        assertThat(facts.evidenceIds()).contains(1L, 2L);
        assertThat(facts.parts().stream().map(MoneyStoryObservation.Part::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add))
                .isEqualByComparingTo(facts.total());
    }
    @Test void parentsSupportKeepsBothPaymentsAndFlagsPossibleRepeatsWithoutDeletingThem() throws Exception {
        var candidate = candidates().stream().filter(c -> "Family".equals(c.category())).findFirst().orElseThrow();
        assertThat(candidate.observation().subject()).isEqualTo("Parents Support");
        assertThat(candidate.observation().total()).isEqualByComparingTo("20500");
        assertThat(candidate.observation().possibleRepeatCount()).isEqualTo(2);
        assertThat(candidate.observation().evidenceIds()).containsExactly(15L, 16L, 22L);
    }
    @Test void eachObservationUsesOneCardWithAnInlineEvidenceActionAndSpecificWording() throws Exception {
        var generator = mock(MoneyStoryCopyGenerator.class);
        var user = new AppUserEntity(); user.setCurrency("INR");
        var rendered = new MoneyStoryRenderer(generator).render(dayTen(), user, "logical", 1, Instant.parse("2026-09-11T12:00:00Z"));
        assertThat(rendered.cards()).singleElement().satisfies(card -> {
            assertThat(card.title()).containsIgnoringCase("purchase");
            assertThat(card.body()).contains("₹5,445", "₹662", "Amazon");
            assertThat(card.actions()).extracting(MoneyStoriesService.Action::type).containsExactly("OPEN_EVIDENCE");
            assertThat(card.components()).extracting(MoneyStoriesService.Component::label).contains("Recorded total", "Other expenses");
        });
        assertThat(rendered.cardFace().heading()).hasSizeLessThanOrEqualTo(20).isNotEqualTo("Recorded expenses");
        assertThat(rendered.observation()).isEqualTo(dayTen().observation());
        verifyNoInteractions(generator);
    }
    @Test void aSingleExpenseGetsOneObservationAndNoPaddingCards() throws Exception {
        var expense = SeptemberStoryFixture.expenses().getFirst();
        var selected = new MoneyStorySelector().select(factory.create(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 11), List.of(expense)));
        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().observation().total()).isEqualByComparingTo("600");
        assertThat(selected.getFirst().observation().incompleteClassificationCount()).isEqualTo(1);
    }
    @Test void unclassifiedExpensesRemainVisibleWithoutAnInventedPurpose() throws Exception {
        var expense = SeptemberStoryFixture.expenses().getFirst();
        expense.setCategory(null); expense.setSubcategory(null);
        var selected = new MoneyStorySelector().select(factory.create(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 11), List.of(expense)));
        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().observation().kind()).isEqualTo(MoneyStoryObservation.Kind.SUMMARY);
        assertThat(selected.getFirst().observation().total()).isEqualByComparingTo("600");
    }
    @Test void deletingTheDominantPurchaseChangesTheExplanationRatherThanKeepingStaleFacts() throws Exception {
        var expenses = SeptemberStoryFixture.expenses();
        expenses.stream().filter(tx -> tx.getId() == 27L).findFirst().orElseThrow().setDeletedAt(Instant.now());
        var day = factory.create(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 11), expenses).stream()
                .filter(c -> c.type() == MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY && c.periodStart().equals(LocalDate.of(2026, 9, 10)))
                .findFirst().orElseThrow();
        assertThat(day.observation().total()).isEqualByComparingTo("662");
        assertThat(day.observation().kind()).isNotEqualTo(MoneyStoryObservation.Kind.CONTRIBUTOR);
        assertThat(day.observation().evidenceIds()).doesNotContain(27L);
    }

    @Test void selectionPrioritizesExplanationsAndAvoidsTwoAmazonContributorStories() throws Exception {
        var selected = new MoneyStorySelector().select(candidates());
        assertThat(selected).hasSize(3);
        assertThat(selected).anySatisfy(c -> assertThat(c).isEqualTo(dayTen()));
        assertThat(selected).anySatisfy(c -> assertThat(c).isEqualTo(food()));
        assertThat(selected.stream().filter(c -> c.observation().kind() == MoneyStoryObservation.Kind.CONTRIBUTOR)).hasSize(1);
        assertThat(selected).extracting(c -> c.observation().noveltyKey()).doesNotHaveDuplicates();
    }
}
