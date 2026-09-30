package com.apps.deen_sa.service;

import java.util.Map;

public interface CommitmentCopyGenerator {
    /** A single model turn writes the linked current/next commitment cards. */
    default CommitmentRunwayCopy generateCommitmentRunway(Map<String, String> facts, CommitmentRunwayCopy fallback) {
        return fallback;
    }
    record Copy(String heading, String faceTheme, String theme, String eyebrow, String title, String body,
                String comparisonTitle, String comparisonBody) { }
    record CommitmentRunwayCopy(Copy current, Copy next) { }
}
