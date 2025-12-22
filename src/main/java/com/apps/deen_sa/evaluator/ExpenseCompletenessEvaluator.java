package com.apps.deen_sa.evaluator;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.utils.CompletenessLevelEnum;
import org.springframework.stereotype.Component;

@Component
public class ExpenseCompletenessEvaluator {

    public CompletenessLevelEnum evaluate(ExpenseDto dto) {

        if (!hasMinimal(dto)) {
            return null; // invalid
        }

        if (!hasOperational(dto)) {
            return CompletenessLevelEnum.MINIMAL;
        }

        if (!hasFinancial(dto)) {
            return CompletenessLevelEnum.OPERATIONAL;
        }

        return CompletenessLevelEnum.FINANCIAL;
    }

    private boolean hasMinimal(ExpenseDto dto) {
        return dto.getAmount() != null
                && dto.getCategory() != null
                && dto.getTransactionDate() != null;
    }

    private boolean hasOperational(ExpenseDto dto) {
        return dto.getSourceAccount() != null;
    }

    private boolean hasFinancial(ExpenseDto dto) {
        return dto.getSourceAccount() != null
                && dto.isSourceResolved(); // container resolved
    }
}
