package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseValidatorTest {

    @Test
    void requiresBothCategoryAndSubcategoryBeforeSavingAnExpense() {
        ExpenseDto expense = new ExpenseDto();
        expense.setAmount(new BigDecimal("250"));
        expense.setTransactionDate(LocalDate.of(2026, 8, 25));
        expense.setSourceAccount("CASH");
        expense.setCategory("Food & Dining");

        assertThat(ExpenseValidator.findMissingFields(expense)).containsExactly("subcategory");

        expense.setSubcategory("Eating Out");
        assertThat(ExpenseValidator.findMissingFields(expense)).isEmpty();
    }
}
