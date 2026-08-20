package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.AccountSetupDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountSetupValidator {

    public static List<String> findMissingFields(AccountSetupDto dto) {

        List<String> missing = new ArrayList<>();

        if (dto.getContainerType() == null) missing.add("containerType");
        if (dto.getName() == null) missing.add("name");

        normalizeAccountIdentifier(dto);
        normalizeCreditCardDetails(dto);

        if ("CREDIT_CARD".equals(dto.getContainerType())) {
            Map<String, Object> details = dto.getDetails();
            if (details == null || details.get("billingDay") == null) missing.add("billingDay");
            if (details == null || details.get("dueDay") == null) missing.add("dueDay");
        }

        return missing;
    }

    /**
     * A user-provided account label is itself a safe identifier. For example,
     * "HDFC bank account" is sufficient when the user has not supplied a masked
     * number or a separate nickname.
     */
    private static void normalizeAccountIdentifier(AccountSetupDto dto) {
        if (("BANK_ACCOUNT".equals(dto.getContainerType())
                || "CREDIT_CARD".equals(dto.getContainerType()))
                && (dto.getExternalRefId() == null || dto.getExternalRefId().isBlank())
                && dto.getName() != null && !dto.getName().isBlank()) {
            dto.setExternalRefId(dto.getName());
        }
    }

    /**
     * Keep persisted account details canonical even if the extractor returns the
     * common date aliases instead of the canonical monthly day keys.
     */
    private static Map<String, Object> normalizeCreditCardDetails(AccountSetupDto dto) {
        Map<String, Object> details = dto.getDetails();
        if (details == null) return null;

        Map<String, Object> normalized = new HashMap<>(details);
        moveAlias(normalized, "dueDate", "dueDay");
        moveAlias(normalized, "billingDate", "billingDay");
        moveAlias(normalized, "billGenerationDay", "billingDay");
        moveAlias(normalized, "statementDay", "billingDay");
        dto.setDetails(normalized);
        return normalized;
    }

    private static void moveAlias(Map<String, Object> details, String alias, String canonical) {
        if (!details.containsKey(canonical) && details.containsKey(alias)) {
            details.put(canonical, details.get(alias));
        }
        details.remove(alias);
    }
}
