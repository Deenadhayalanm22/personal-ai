package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.core.state.StateContainerEntity;

import java.math.BigDecimal;

public interface CreditSettlementStrategy {
    void applyPayment(StateContainerEntity container, BigDecimal amount);
}
