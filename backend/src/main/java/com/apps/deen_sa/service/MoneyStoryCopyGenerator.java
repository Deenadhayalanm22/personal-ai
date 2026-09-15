package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryType;
import java.util.Map;

public interface MoneyStoryCopyGenerator {
    Copy generate(MoneyStoryType type, Map<String, String> facts, Copy fallback);
    record Copy(String heading, String faceTheme, String theme, String eyebrow, String title, String body,
                String comparisonTitle, String comparisonBody) { }
}
