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

        // Credit-card-specific rules
        if ("CREDIT_CARD".equals(dto.getContainerType())) {

            if (dto.getCapacityLimit() == null)
                missing.add("capacityLimit");

            Map<String, Object> details = normalizeCreditCardDetails(dto);
            if (details == null || !details.containsKey("dueDay"))
                missing.add("details.dueDay");
        }

        if (dto.getCurrency() == null)
            missing.add("currency");

        return missing;
    }

    /**
     * Keep persisted account details canonical even if the extractor returns the
     * common semantic alias "dueDate" instead of the contract key "dueDay".
     */
    private static Map<String, Object> normalizeCreditCardDetails(AccountSetupDto dto) {
        Map<String, Object> details = dto.getDetails();
        if (details == null || details.containsKey("dueDay") || !details.containsKey("dueDate")) {
            return details;
        }

        Map<String, Object> normalized = new HashMap<>(details);
        normalized.put("dueDay", normalized.remove("dueDate"));
        dto.setDetails(normalized);
        return normalized;
    }
}
