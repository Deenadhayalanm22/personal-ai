package com.apps.deen_sa.finance.payment;

import com.apps.deen_sa.dto.LiabilityPaymentDto;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiabilityPaymentSourceResolutionTest {

    @Test
    void debitsExplicitlyNamedBankAccountInsteadOfFirstBankAccount() {
        StateContainerEntity foodCard = container(1L, "BANK_ACCOUNT", "HDFC Food Card bank account");
        StateContainerEntity bankAccount = container(2L, "BANK_ACCOUNT", "HDFC bank account");
        StateContainerEntity creditCard = container(3L, "CREDIT_CARD", "HDFC credit card");

        LiabilityPaymentDto payment = new LiabilityPaymentDto();
        payment.setSourceAccount("BANK_ACCOUNT");
        payment.setRawText(
                "Paid HDFC Credit Card bill amount ₹61,299 directly from my HDFC bank account via Net Banking.");

        assertThat(LiabilityPaymentHandler.resolveSourceContainer(
                payment, List.of(foodCard, bankAccount, creditCard))).isSameAs(bankAccount);
    }

    private static StateContainerEntity container(long id, String type, String name) {
        StateContainerEntity container = new StateContainerEntity();
        container.setId(id);
        container.setContainerType(type);
        container.setName(name);
        return container;
    }
}
