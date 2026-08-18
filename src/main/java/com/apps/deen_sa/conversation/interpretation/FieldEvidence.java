package com.apps.deen_sa.conversation.interpretation;

public record FieldEvidence(String field, String value, String evidence, Double confidence)
        implements com.apps.deen_sa.extension.api.FactEvidence { }
