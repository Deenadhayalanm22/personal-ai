package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.expense.ExpenseRecordStatus;
import com.apps.deen_sa.finance.expense.correction.ExpenseCorrectionService;
import com.apps.deen_sa.finance.tag.*;
import com.apps.deen_sa.finance.legacy.state.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebExpenseServiceTest {
    private final StateChangeRepository expenses = mock(StateChangeRepository.class);
    private final StateContainerRepository accounts = mock(StateContainerRepository.class);
    private final ExpenseCorrectionService corrections = mock(ExpenseCorrectionService.class);
    private final WebExpenseTaxonomyService taxonomy = mock(WebExpenseTaxonomyService.class);
    private final TagRepository tags = mock(TagRepository.class);
    private final TransactionTagRepository transactionTags = mock(TransactionTagRepository.class);
    private final WebExpenseService service = new WebExpenseService(
            expenses, accounts, corrections, taxonomy, tags, transactionTags);
    private final AppUserEntity user = new AppUserEntity();

    @BeforeEach
    void setUp() {
        user.setId(42L); user.setTimezone("Asia/Kolkata"); user.setCurrency("INR");
        when(transactionTags.findAllByTransactionIdIn(anyCollection())).thenReturn(List.of());
        when(tags.findAllById(anyCollection())).thenReturn(List.of());
    }

    @Test
    void listsOnlyTheAuthenticatedUsersMonthWithCursorPagination() {
        StateChangeEntity first = expense(12L, "Food", 1);
        StateChangeEntity second = expense(11L, "Travel", 2);
        when(expenses.findFilteredActiveExpensesBefore(eq("42"), any(), any(), isNull(), eq(false), eq(""),
                eq(false), eq(""), isNull(), any()))
                .thenReturn(List.of(first, second));
        when(expenses.summarizeFilteredActiveExpenses(eq("42"), any(), any(), isNull(), eq(false), eq(""),
                eq(false), eq("")))
                .thenReturn(java.util.Collections.singletonList(new Object[]{2L, new BigDecimal("200")}));

        var page = service.list(user, YearMonth.of(2026, 8), 1, null, null);

        assertThat(page.items()).extracting(WebExpenseService.ExpenseItem::id).containsExactly(12L);
        assertThat(page.nextBeforeId()).isEqualTo(12L);
        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenses).findFilteredActiveExpensesBefore(eq("42"), start.capture(), end.capture(),
                isNull(), eq(false), eq(""), eq(false), eq(""), isNull(), pageable.capture());
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-07-31T18:30:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-08-31T18:30:00Z"));
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        assertThat(page.filterSummary().transactionCount()).isEqualTo(2);
        assertThat(page.filterSummary().totalAmount()).isEqualByComparingTo("200");
    }

    @Test
    void appliesAccountCategoryAndSubcategoryAsOneFilter() {
        StateChangeEntity result = expense(12L, "Food", 1);
        result.setSubcategory("Restaurants");
        when(expenses.findFilteredActiveExpensesBefore(eq("42"), any(), any(), eq(7L), eq(true), eq("Food"),
                eq(true), eq("Restaurants"), isNull(), any())).thenReturn(List.of(result));
        when(expenses.summarizeFilteredActiveExpenses(eq("42"), any(), any(), eq(7L), eq(true), eq("Food"),
                eq(true), eq("Restaurants"))).thenReturn(
                        java.util.Collections.singletonList(new Object[]{1L, new BigDecimal("100")}));

        var page = service.list(user, YearMonth.of(2026, 8), 20, null,
                new WebExpenseService.ExpenseFilter(7L, " Food ", " Restaurants "));

        assertThat(page.items()).extracting(WebExpenseService.ExpenseItem::id).containsExactly(12L);
        assertThat(page.filterSummary()).isEqualTo(new WebExpenseService.FilterSummary(
                1, new BigDecimal("100"), "INR", 7L, "Food", "Restaurants"));
    }

    @Test
    void filtersByAnySelectedTagWithoutChangingPageSummarySemantics() {
        StateChangeEntity result = expense(12L, "Travel", 1);
        when(tags.findAllByUserIdAndIdIn(eq(42L), anyCollection()))
                .thenReturn(List.of(tag(4L, "Travel"), tag(7L, "Work")));
        when(expenses.findTagFilteredActiveExpensesBefore(eq("42"), any(), any(), isNull(), eq(false), eq(""),
                eq(false), eq(""), eq(List.of(4L, 7L)), eq(false), eq(2L), isNull(), any()))
                .thenReturn(List.of(result));
        when(expenses.summarizeTagFilteredActiveExpenses(eq("42"), any(), any(), isNull(), eq(false), eq(""),
                eq(false), eq(""), eq(List.of(4L, 7L)), eq(false), eq(2L)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{1L, new BigDecimal("2400")}));

        var page = service.list(user, YearMonth.of(2026, 8), 20, null,
                new WebExpenseService.ExpenseFilter(null, null, null, List.of(4L, 7L),
                        WebExpenseService.TagMatch.ANY));

        assertThat(page.items()).extracting(WebExpenseService.ExpenseItem::id).containsExactly(12L);
        assertThat(page.filterSummary().transactionCount()).isEqualTo(1);
        assertThat(page.filterSummary().totalAmount()).isEqualByComparingTo("2400");
        assertThat(page.filterSummary().tagIds()).containsExactly(4L, 7L);
        assertThat(page.filterSummary().tagMatch()).isEqualTo("any");
        verify(expenses, never()).findFilteredActiveExpensesBefore(any(), any(), any(), any(), anyBoolean(), any(),
                anyBoolean(), any(), any(), any());
    }

    @Test
    void supportsAllTagIntersectionAndDeduplicatesRequestedIds() {
        when(tags.findAllByUserIdAndIdIn(eq(42L), anyCollection()))
                .thenReturn(List.of(tag(4L, "Travel"), tag(7L, "Work")));
        when(expenses.findTagFilteredActiveExpensesBefore(eq("42"), any(), any(), isNull(), eq(false), eq(""),
                eq(false), eq(""), eq(List.of(4L, 7L)), eq(true), eq(2L), isNull(), any()))
                .thenReturn(List.of());
        when(expenses.summarizeTagFilteredActiveExpenses(eq("42"), any(), any(), isNull(), eq(false), eq(""),
                eq(false), eq(""), eq(List.of(4L, 7L)), eq(true), eq(2L)))
                .thenReturn(java.util.Collections.singletonList(new Object[]{0L, BigDecimal.ZERO}));

        var page = service.list(user, YearMonth.of(2026, 8), 20, null,
                new WebExpenseService.ExpenseFilter(null, null, null, List.of(4L, 4L, 7L),
                        WebExpenseService.TagMatch.ALL));

        assertThat(page.items()).isEmpty();
        assertThat(page.filterSummary().transactionCount()).isZero();
        assertThat(page.filterSummary().tagIds()).containsExactly(4L, 7L);
        assertThat(page.filterSummary().tagMatch()).isEqualTo("all");
    }

    @Test
    void rejectsTagFilterWhenAnySelectedTagIsUnavailableToUser() {
        when(tags.findAllByUserIdAndIdIn(eq(42L), anyCollection())).thenReturn(List.of(tag(4L, "Travel")));

        assertThatThrownBy(() -> service.list(user, YearMonth.of(2026, 8), 20, null,
                new WebExpenseService.ExpenseFilter(null, null, null, List.of(4L, 99L),
                        WebExpenseService.TagMatch.ANY)))
                .isInstanceOf(WebApiException.class).hasMessage("One or more selected tags are unavailable");
        verifyNoInteractions(expenses);
    }

    @Test
    void classificationEditUsesAuthenticatedUserAndExpectedVersion() {
        StateChangeEntity replacement = expense(15L, "Food", 4);
        replacement.setSubcategory("Groceries");
        when(corrections.editClassification(42L, 12L, 3, "Food", "Groceries"))
                .thenReturn(replacement);
        when(taxonomy.validate("Food", "Groceries"))
                .thenReturn(new WebExpenseTaxonomyService.Classification("Food", "Groceries"));

        var result = service.editClassification(user, 12L,
                new WebExpenseService.ClassificationUpdate(" Food ", " Groceries ", 3, null));

        assertThat(result.id()).isEqualTo(15L);
        assertThat(result.version()).isEqualTo(4);
        verify(corrections).editClassification(42L, 12L, 3, "Food", "Groceries");
    }

    @Test
    void rejectsBlankClassification() {
        assertThatThrownBy(() -> service.editClassification(user, 12L,
                new WebExpenseService.ClassificationUpdate(" ", null, 1, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400 BAD_REQUEST");
        verifyNoInteractions(corrections);
    }

    @Test
    void editCanReplaceExpenseTagsWithOwnedTagIds() {
        StateChangeEntity replacement = expense(15L, "Food", 4);
        TagEntity pondicherry = tag(8L, "Pondicherry");
        TagEntity family = tag(9L, "Family");
        when(tags.findAllByUserIdAndIdIn(eq(42L), anyCollection())).thenReturn(List.of(pondicherry, family));
        when(corrections.editClassification(42L, 12L, 3, null, null)).thenReturn(replacement);
        when(transactionTags.findAllByTransactionIdIn(anyCollection())).thenAnswer(invocation -> {
            var link1 = new TransactionTagEntity(); link1.setTransactionId(15L); link1.setTagId(8L);
            var link2 = new TransactionTagEntity(); link2.setTransactionId(15L); link2.setTagId(9L);
            return List.of(link1, link2);
        });
        when(tags.findAllById(anyCollection())).thenReturn(List.of(pondicherry, family));

        var result = service.editClassification(user, 12L,
                new WebExpenseService.ClassificationUpdate(null, null, 3, List.of(8L, 9L)));

        assertThat(result.tags()).extracting(WebExpenseService.TagItem::name)
                .containsExactly("Family", "Pondicherry");
        verify(transactionTags).saveAll(argThat(values -> {
            List<TransactionTagEntity> links = new java.util.ArrayList<>();
            values.forEach(links::add);
            return links.size() == 2 && links.stream().allMatch(link -> link.getTransactionId().equals(15L));
        }));
    }

    @Test
    void rejectsTagsThatDoNotBelongToAuthenticatedUser() {
        when(tags.findAllByUserIdAndIdIn(eq(42L), anyCollection())).thenReturn(List.of(tag(8L, "Mine")));

        assertThatThrownBy(() -> service.editClassification(user, 12L,
                new WebExpenseService.ClassificationUpdate(null, null, 3, List.of(8L, 99L))))
                .isInstanceOf(WebApiException.class).hasMessage("One or more selected tags are unavailable");
        verifyNoInteractions(corrections);
    }

    private TagEntity tag(Long id, String name) {
        TagEntity value = new TagEntity(); value.setId(id); value.setUserId(42L); value.setName(name);
        return value;
    }

    private StateChangeEntity expense(Long id, String category, int version) {
        StateChangeEntity value = new StateChangeEntity();
        value.setId(id); value.setUserId("42"); value.setAmount(new BigDecimal("100"));
        value.setTimestamp(Instant.parse("2026-08-12T08:00:00Z")); value.setCategory(category);
        value.setRecordStatus(ExpenseRecordStatus.ACTIVE); value.setRecordVersion(version);
        value.setTransactionType(StateChangeTypeEnum.EXPENSE); value.setRawText("Paid 100");
        return value;
    }
}
