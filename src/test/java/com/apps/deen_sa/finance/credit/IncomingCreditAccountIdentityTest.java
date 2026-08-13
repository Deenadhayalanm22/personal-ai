package com.apps.deen_sa.finance.credit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncomingCreditAccountIdentityTest {

    @Test
    void preservesAccountQualifiersNeededForExactSelection() {
        assertThat(IncomingCreditHandler.accountIdentity("my HDFC bank account"))
                .isEqualTo(IncomingCreditHandler.accountIdentity("HDFC Bank Account"))
                .isNotEqualTo(IncomingCreditHandler.accountIdentity("HDFC Food Card bank account"))
                .isNotEqualTo(IncomingCreditHandler.accountIdentity("HDFC Petrol Card bank account"));
    }

    @Test
    void rejectsContextInferredDestinationThatIsAbsentFromCurrentMessage() {
        assertThat(IncomingCreditHandler.destinationGrounded(
                "HDFC bank account", "Salary credited: ₹1,00,000 received today."))
                .isFalse();
    }

    @Test
    void acceptsDestinationExplicitlyStatedInEventOrFollowupMessage() {
        assertThat(IncomingCreditHandler.destinationGrounded(
                "HDFC Food Card bank account",
                "₹4,000 allowance was credited to my HDFC Food Card bank account"))
                .isTrue();
        assertThat(IncomingCreditHandler.destinationGrounded(
                "HDFC bank account", "HDFC bank account"))
                .isTrue();
    }
}
