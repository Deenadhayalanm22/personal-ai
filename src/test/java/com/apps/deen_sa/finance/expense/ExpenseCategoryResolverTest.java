package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.llm.impl.TagSemanticMatcher;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCategoryResolverTest {
    private final ExpenseTaxonomyRegistry taxonomy = new ExpenseTaxonomyRegistry();

    @Test
    void acceptsOnlyModelSelectionsThatExistInConfiguredTaxonomy() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcher("weekly sabzi", "Groceries"));
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("weekly sabzi");

        resolver.canonicalize(expense, "Paid 900 for weekly sabzi");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isEqualTo("Groceries");
    }

    @Test
    void keepsAnAlreadyValidPairStableWithoutCallingTheSemanticModelAgain() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("Food & Dining");
        expense.setSubcategory("Groceries");

        resolver.canonicalize(expense, "I spent 1300 on groceries");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isEqualTo("Groceries");
    }

    @Test
    void rejectsInventedModelLabelsInsteadOfPersistingFreeText() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcher("something unusual", "Made Up"));
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("something unusual");

        resolver.canonicalize(expense, "Paid 900 for something unusual");

        assertThat(expense.getCategory()).isNull();
        assertThat(expense.getSubcategory()).isNull();
    }

    @Test
    void resolvesEachSplitExpenseFromItsOwnMerchantInsteadOfTheCombinedSentence() {
        ExpenseDto tea = new ExpenseDto();
        tea.setMerchantName("tea");
        new ExpenseCategoryResolver(taxonomy, matcherMustNotRun())
                .canonicalize(tea, "Spent 80 on tea and 120 on auto using UPI");

        ExpenseDto auto = new ExpenseDto();
        auto.setMerchantName("auto");
        new ExpenseCategoryResolver(taxonomy, matcherMustNotRun())
                .canonicalize(auto, "Spent 80 on tea and 120 on auto using UPI");

        assertThat(tea.getCategory()).isEqualTo("Food & Dining");
        assertThat(tea.getSubcategory()).isEqualTo("Snacks & Beverages");
        assertThat(auto.getCategory()).isEqualTo("Transportation");
        assertThat(auto.getSubcategory()).isEqualTo("Public Transport");
    }

    @Test
    void refinesBroadCategoryWithinItsConfiguredSubcategoriesForBudgetAccounting() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy,
                matcherWithin("BBQ Nation family dinner", "Eating Out"));
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("Food & Dining");
        expense.setMerchantName("BBQ Nation family dinner");

        resolver.canonicalize(expense, "Dinner with family at BBQ Nation");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isEqualTo("Eating Out");
    }

    @Test
    void doesNotAcceptAResolvedSubcategoryFromAnotherParentCategory() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy,
                matcherWithin("family meal venue", "Fuel"));
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("Food & Dining");
        expense.setMerchantName("family meal venue");

        resolver.canonicalize(expense, "Family meal at a venue");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isNull();
    }

    @Test
    void resolvesExplicitDiningLanguageWithoutAFlakySemanticModelCall() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("Food & Dining");
        expense.setMerchantName("Swiggy lunch order at office");

        resolver.canonicalize(expense, "Swiggy lunch order at office for ₹340");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isEqualTo("Eating Out");
    }

    @Test
    void explicitBudgetSubcategoryOverridesBroaderModelProposal() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());

        assertThat(resolver.resolveBudgetScope("Food & Dining",
                "Setup my eating out budget ₹5,000 for this month."))
                .contains("Eating Out");
    }

    @Test
    void correctsCanonicalButWrongSiblingUsingTransactionEvidence() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy,
                matcherWithin("Social Bar team lunch", "Eating Out"));
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("Food & Dining");
        expense.setSubcategory("Celebration Meal/Home Cooked");
        expense.setMerchantName("Social Bar team lunch");

        resolver.canonicalize(expense, "Team lunch at Social Bar");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isEqualTo("Eating Out");
    }

    @Test
    void resolvesTermInsurancePremiumFromConfiguredTaxonomyWithoutModelCall() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();
        expense.setMerchantName("Yearly term insurance premium");

        resolver.canonicalize(expense,
                "Yearly term insurance premium of ₹50,000 paid using HDFC Credit Card.");

        assertThat(expense.getCategory()).isEqualTo("Insurance");
        assertThat(expense.getSubcategory()).isEqualTo("Life Insurance");
    }

    @Test
    void resolvesCarServiceFromConfiguredTaxonomyWithoutModelCall() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();
        expense.setMerchantName("Car minor service and wash at local garage");

        resolver.canonicalize(expense,
                "Car minor service and wash at local garage cost ₹2,500, paid using my HDFC bank account via UPI.");

        assertThat(expense.getCategory()).isEqualTo("Transportation");
        assertThat(expense.getSubcategory()).isEqualTo("Vehicle Maintenance");
    }

    @Test
    void prefersSpecificInstamartAliasOverBroadSwiggyAlias() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();
        expense.setMerchantName("Swiggy Instamart ice cream order");

        resolver.canonicalize(expense, "Swiggy Instamart ice cream order for ₹310 using food card.");

        assertThat(expense.getCategory()).isEqualTo("Food & Dining");
        assertThat(expense.getSubcategory()).isEqualTo("Groceries");
    }

    @Test
    void mapsSchoolBagsToSchoolSuppliesInsteadOfSchoolFees() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();
        expense.setMerchantName("buying school bags paid");

        resolver.canonicalize(expense, "Spent 760 on buying school bags paid using UPI yesterday");

        assertThat(expense.getCategory()).isEqualTo("Education");
        assertThat(expense.getSubcategory()).isEqualTo("School Supplies");
    }

    @Test
    void mapsTransferToSpouseToFamilySupportWithoutCategoryFollowup() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();

        resolver.canonicalize(expense,
                "Transferred 2000 to my wife from my hdfc bank account");

        assertThat(expense.getCategory()).isEqualTo("Family Support");
        assertThat(expense.getSubcategory()).isEqualTo("Family Transfer");
    }

    @Test
    void canonicalizesFamilySharingFollowupToFamilyTransfer() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto expense = new ExpenseDto();
        expense.setCategory("Family sharing");

        resolver.canonicalize(expense, "Family sharing");

        assertThat(expense.getCategory()).isEqualTo("Family Support");
        assertThat(expense.getSubcategory()).isEqualTo("Family Transfer");
    }

    @Test
    void distinguishesParentSupportFromSpouseTransferInExistingMonthlyScenario() {
        ExpenseCategoryResolver resolver = new ExpenseCategoryResolver(taxonomy, matcherMustNotRun());
        ExpenseDto mom = new ExpenseDto();
        ExpenseDto wife = new ExpenseDto();

        resolver.canonicalize(mom, "Sent ₹10,000 from my HDFC bank account to my mom via UPI.");
        resolver.canonicalize(wife, "Sent ₹10,000 from my HDFC bank account to my wife via UPI.");

        assertThat(mom.getCategory()).isEqualTo("Family Support");
        assertThat(mom.getSubcategory()).isEqualTo("Parents Support");
        assertThat(wife.getCategory()).isEqualTo("Family Support");
        assertThat(wife.getSubcategory()).isEqualTo("Family Transfer");
    }

    private TagSemanticMatcher matcherMustNotRun() {
        return new TagSemanticMatcher(null, null) {
            @Override public Map<String, String> match(List<String> canonical, List<String> values) {
                throw new AssertionError("Configured aliases must resolve without a model call");
            }
        };
    }

    private TagSemanticMatcher matcher(String raw, String resolved) {
        return new TagSemanticMatcher(null, null) {
            @Override public Map<String, String> match(List<String> canonical, List<String> values) {
                assertThat(canonical).contains("Groceries", "Fuel", "Medicines");
                return Map.of(raw, resolved);
            }
        };
    }

    private TagSemanticMatcher matcherWithin(String raw, String resolved) {
        return new TagSemanticMatcher(null, null) {
            @Override public Map<String, String> match(List<String> canonical, List<String> values) {
                assertThat(canonical).containsExactlyInAnyOrder(
                        "Groceries", "Eating Out", "Snacks & Beverages", "Celebration Meal/Home Cooked");
                assertThat(values).containsExactly(raw);
                return Map.of(raw, resolved);
            }
        };
    }
}
