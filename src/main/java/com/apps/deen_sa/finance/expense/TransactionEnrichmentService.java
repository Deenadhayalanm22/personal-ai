package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validates and merges optional user-stated semantics without changing established transaction facts. */
@Service
public class TransactionEnrichmentService {
    public static final Set<String> SUPPORTED_DETAILS = Set.of(
            "beneficiary", "purpose", "occasion", "plannedStatus", "reimbursable", "tripContext");
    private static final Set<String> PLANNED_VALUES = Set.of("PLANNED", "UNPLANNED", "UNKNOWN");

    public ExpenseDto merge(ExpenseDto candidate, TransactionEnrichment enrichment) {
        if (candidate == null) throw new IllegalArgumentException("Pending expense is required");
        if (enrichment == null || enrichment.source() == EnrichmentSource.UNKNOWN) return candidate;
        ExpenseDto proposal = enrichment.proposal();
        if (proposal == null) return candidate;
        if (hasText(proposal.getSourceAccount())) candidate.setSourceAccount(proposal.getSourceAccount().trim());
        if (proposal.getDetails() == null || proposal.getDetails().isEmpty()) return candidate;

        Map<String, Object> merged = candidate.getDetails() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(candidate.getDetails());
        proposal.getDetails().forEach((key, value) -> {
            if (!SUPPORTED_DETAILS.contains(key)) throw new IllegalArgumentException("Unsupported enrichment: " + key);
            Object validated = validate(key, value);
            if (validated != null) merged.put(key, validated);
        });
        candidate.setDetails(merged);
        return candidate;
    }

    private Object validate(String key, Object value) {
        if (value == null) return null;
        if ("reimbursable".equals(key)) {
            if (value instanceof Boolean) return value;
            String normalized = value.toString().trim().toLowerCase(Locale.ROOT);
            if (Set.of("true", "yes", "reimbursable").contains(normalized)) return true;
            if (Set.of("false", "no", "not reimbursable").contains(normalized)) return false;
            throw new IllegalArgumentException("Invalid reimbursable value");
        }
        String normalized = value.toString().trim();
        if (normalized.isBlank()) return null;
        if (normalized.length() > 120) throw new IllegalArgumentException("Enrichment value is too long");
        if ("plannedStatus".equals(key)) {
            normalized = normalized.toUpperCase(Locale.ROOT);
            if (!PLANNED_VALUES.contains(normalized)) throw new IllegalArgumentException("Invalid planned status");
        }
        return normalized;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
