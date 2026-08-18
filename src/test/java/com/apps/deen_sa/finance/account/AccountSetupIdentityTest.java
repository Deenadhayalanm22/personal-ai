package com.apps.deen_sa.finance.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountSetupIdentityTest {

    @Test
    void normalizesCosmeticNameDifferencesForDuplicateDetection() {
        assertThat(AccountSetupHandler.normalizeIdentity("My HDFC Bank Account"))
                .isEqualTo(AccountSetupHandler.normalizeIdentity("hdfc-bank account"));
    }

    @Test
    void keepsDistinctNamedAccountsSeparate() {
        assertThat(AccountSetupHandler.normalizeIdentity("HDFC bank account"))
                .isNotEqualTo(AccountSetupHandler.normalizeIdentity("HDFC salary bank account"))
                .isNotEqualTo(AccountSetupHandler.normalizeIdentity("HDFC Food Card bank account"));
    }
}
