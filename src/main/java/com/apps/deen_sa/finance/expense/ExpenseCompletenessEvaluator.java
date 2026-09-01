package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import org.springframework.stereotype.Component;

@Component
public class ExpenseCompletenessEvaluator {

    private static final java.util.List<String> ENRICHMENT_FIELDS = java.util.List.of(
            "beneficiary", "purpose", "occasion", "plannedStatus", "reimbursable", "tripContext", "sourceAccount");

    public ExpenseCompleteness assess(ExpenseDto dto) {
        java.util.List<String> required = ExpenseValidator.findMissingFields(dto);
        java.util.Map<String, Object> details = dto.getDetails() == null ? java.util.Map.of() : dto.getDetails();
        java.util.List<String> enrichment = ENRICHMENT_FIELDS.stream()
                .filter(field -> "sourceAccount".equals(field)
                        ? dto.getSourceAccount() == null || dto.getSourceAccount().isBlank()
                        : !details.containsKey(field) || details.get(field) == null)
                .toList();
        ExpenseCompleteness result = new ExpenseCompleteness(required, enrichment);
        dto.setMissingFields(required); // legacy compatibility
        dto.setMissingRequiredFields(required);
        dto.setMissingEnrichmentFields(enrichment);
        return result;
    }

    public CompletenessLevelEnum evaluate(ExpenseDto dto) {
        ExpenseCompleteness assessment = assess(dto);
        if (!hasMinimal(dto)) {
            return null; // invalid
        }

        if (!assessment.confirmable() || !hasOperational(dto)) {
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
