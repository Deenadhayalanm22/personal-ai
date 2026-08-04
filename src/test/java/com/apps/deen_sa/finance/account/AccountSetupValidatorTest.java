package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.AccountSetupDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountSetupValidatorTest {

    @Test
    void acceptsAndCanonicalizesDueDateAliasFromExtractor() {
        AccountSetupDto dto = validCreditCard(Map.of("dueDate", 21));

        assertThat(AccountSetupValidator.findMissingFields(dto)).isEmpty();
        assertThat(dto.getDetails())
                .containsEntry("dueDay", 21)
                .doesNotContainKey("dueDate");
    }

    @Test
    void acceptsCanonicalDueDay() {
        AccountSetupDto dto = validCreditCard(Map.of("dueDay", 21));

        assertThat(AccountSetupValidator.findMissingFields(dto)).isEmpty();
        assertThat(dto.getDetails()).containsEntry("dueDay", 21);
    }

    private AccountSetupDto validCreditCard(Map<String, Object> details) {
        AccountSetupDto dto = new AccountSetupDto();
        dto.setContainerType("CREDIT_CARD");
        dto.setName("hdfc credit card");
        dto.setCurrency("INR");
        dto.setCapacityLimit(BigDecimal.valueOf(30_000));
        dto.setDetails(details);
        return dto;
    }
}
