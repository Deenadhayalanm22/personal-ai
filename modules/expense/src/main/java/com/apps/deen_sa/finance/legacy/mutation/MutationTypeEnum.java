package com.apps.deen_sa.finance.legacy.mutation;

public enum MutationTypeEnum {
    DEBIT,    // value increases for liabilities, decreases for assets
    CREDIT,   // value decreases for liabilities, increases for assets
    PAYMENT   // explicit liability settlement
}
