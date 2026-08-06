package com.apps.deen_sa.conversation.interpretation;

/** Closed, language-independent periods that the analytics layer can execute safely. */
public enum QueryPeriod {
    NONE,
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    LAST_MONTH,
    LAST_3_MONTHS
}
