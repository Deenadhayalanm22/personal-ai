package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.core.state.CompletenessLevelEnum;
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

        if (!hasFinancial(dto)) {
            dto.setCompletenessLevelEnum(CompletenessLevelEnum.OPERATIONAL);
            return CompletenessLevelEnum.OPERATIONAL;
        }

        dto.setCompletenessLevelEnum(CompletenessLevelEnum.FINANCIAL);
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
