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

    @Test
    void bankAccountCanStartWithoutOpeningBalance() {
        AccountSetupDto dto = new AccountSetupDto();
        dto.setContainerType("BANK_ACCOUNT");
        dto.setName("HDFC savings account");
        dto.setCurrency("INR");

        assertThat(AccountSetupValidator.findMissingFields(dto)).isEmpty();
        assertThat(dto.getExternalRefId()).isEqualTo("HDFC savings account");
    }

    @Test
    void bankAccountAcceptsExplicitZeroBalance() {
        AccountSetupDto dto = new AccountSetupDto();
        dto.setContainerType("BANK_ACCOUNT");
        dto.setName("HDFC savings account");
        dto.setCurrency("INR");
        dto.setCurrentValue(BigDecimal.ZERO);
        dto.setExternalRefId("Savings-1234");

        assertThat(AccountSetupValidator.findMissingFields(dto)).isEmpty();
    }

    @Test
    void creditCardCanStartWithoutOutstandingLimitOrDueDay() {
        AccountSetupDto dto = validCreditCard(Map.of("dueDay", 21));
        dto.setCurrentValue(null);
        dto.setExternalRefId(null);

        dto.setCapacityLimit(null);
        dto.setDetails(null);

        assertThat(AccountSetupValidator.findMissingFields(dto)).isEmpty();
        assertThat(dto.getExternalRefId()).isEqualTo("hdfc credit card");
    }

    @Test
    void accountWithoutNameCannotDeriveIdentifier() {
        AccountSetupDto dto = new AccountSetupDto();
        dto.setContainerType("BANK_ACCOUNT");
        dto.setCurrency("INR");
        dto.setCurrentValue(BigDecimal.valueOf(40_000));

        assertThat(AccountSetupValidator.findMissingFields(dto))
                .containsExactly("name");
    }

    private AccountSetupDto validCreditCard(Map<String, Object> details) {
        AccountSetupDto dto = new AccountSetupDto();
        dto.setContainerType("CREDIT_CARD");
        dto.setName("hdfc credit card");
        dto.setCurrency("INR");
        dto.setCurrentValue(BigDecimal.ZERO);
        dto.setExternalRefId("Card-1234");
        dto.setCapacityLimit(BigDecimal.valueOf(30_000));
        dto.setDetails(details);
        return dto;
    }
}
