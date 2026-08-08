package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import com.apps.deen_sa.dto.ExpenseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCompletenessEvaluatorTest {
    private final ExpenseCompletenessEvaluator evaluator = new ExpenseCompletenessEvaluator();

    @Test
    void amountDateAndSourceWithoutCategoryRemainMinimal() {
        ExpenseDto expense = new ExpenseDto();
        expense.setAmount(new BigDecimal("3500"));
        expense.setTransactionDate(LocalDate.of(2026, 8, 5));
        expense.setSourceAccount("BANK_ACCOUNT");

        assertThat(evaluator.evaluate(expense)).isEqualTo(CompletenessLevelEnum.MINIMAL);
    }
}
