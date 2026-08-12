package com.apps.deen_sa.finance.extension;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceInterpretationPromptTest {

    @Test
    void distinguishesPersonPaymentsFromOwnedAccountTransfersAndAssetSales() {
        String instructions = new FinanceInterpretationPrompt().instructions();

        assertThat(instructions)
                .contains("Money sent or paid to another person")
                .contains("is EXPENSE")
                .contains("TRANSFER is only movement between two explicitly owned accounts")
                .contains("ASSET_SELL requires explicit evidence")
                .contains("office/team lunch")
                .contains("are Eating Out")
                .contains("dinner at a restaurant remains Eating Out");
    }
}
