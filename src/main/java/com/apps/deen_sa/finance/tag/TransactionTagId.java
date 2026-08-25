package com.apps.deen_sa.finance.tag;

import java.io.Serializable;
import java.util.Objects;

public class TransactionTagId implements Serializable {
    private Long transactionId;
    private Long tagId;

    public TransactionTagId() { }

    public TransactionTagId(Long transactionId, Long tagId) {
        this.transactionId = transactionId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TransactionTagId that)) return false;
        return Objects.equals(transactionId, that.transactionId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, tagId);
    }
}
