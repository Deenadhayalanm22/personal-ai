package com.apps.deen_sa.validator;

import com.apps.deen_sa.dto.AccountSetupDto;

import java.util.ArrayList;
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

            Map<String, Object> details = dto.getDetails();
            if (details == null || !details.containsKey("dueDay"))
                missing.add("details.dueDay");
        }

        // Payable-specific (Loans) rules
        if ("PAYABLE".equals(dto.getContainerType())) {

            if (dto.getCapacityLimit() == null)
                missing.add("capacityLimit");

            Map<String, Object> details =
                    dto.getDetails() != null ? dto.getDetails() : Map.of();

            if (!details.containsKey("emiAmount"))
                missing.add("details.emiAmount");

            if (!details.containsKey("dueDay"))
                missing.add("details.dueDay");
        }

        if (dto.getCurrency() == null)
            missing.add("currency");

        return missing;
    }
}
