package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import org.springframework.stereotype.Component;

@Component
public class ExpenseCompletenessEvaluator {

    public CompletenessLevelEnum evaluate(ExpenseDto dto) {

        if (!hasMinimal(dto)) {
            return null; // invalid
        }

        if (!hasOperational(dto)) {
            dto.setCompletenessLevelEnum(CompletenessLevelEnum.MINIMAL);
            return CompletenessLevelEnum.MINIMAL;
        }

        dto.setCompletenessLevelEnum(CompletenessLevelEnum.OPERATIONAL);
        return CompletenessLevelEnum.OPERATIONAL;
    }

    private boolean hasMinimal(ExpenseDto dto) {
        return dto.getAmount() != null
                && dto.getTransactionDate() != null;
    }

    private boolean hasOperational(ExpenseDto dto) {
        return dto.getCategory() != null
                && !dto.getCategory().isBlank()
                && dto.getSubcategory() != null
                && !dto.getSubcategory().isBlank();
    }
}
