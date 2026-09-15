package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.MoneyStoryLevel;
import org.springframework.stereotype.Component;
import java.util.*;

/** Prefer explanations, then select a small deck with distinct evidence and subjects. */
@Component
public class MoneyStorySelector {
    // Keep the established per-topic pattern tie-breaks unchanged.
    static final Comparator<MoneyStoryCandidate> ORDER = Comparator
            .comparingInt((MoneyStoryCandidate c) -> c.level() == MoneyStoryLevel.PATTERN ? 0 : 1)
            .thenComparing(MoneyStoryCandidate::periodStart, Comparator.reverseOrder())
            .thenComparing(c -> c.type().name())
            .thenComparing(c -> c.category() == null ? "" : c.category());

    private static final Comparator<MoneyStoryCandidate> DECK_ORDER = Comparator
            .comparingInt((MoneyStoryCandidate c) -> c.level() == MoneyStoryLevel.PATTERN ? 1000
                    : c.observation() == null ? 0 : c.observation().usefulness()
                        + (c.type() == com.apps.deen_sa.domain.MoneyStoryType.UNUSUAL_HIGH_SPEND_DAY
                            && c.observation().kind() == MoneyStoryObservation.Kind.CONTRIBUTOR ? 5 : 0)).reversed()
            .thenComparing(MoneyStoryCandidate::periodEnd, Comparator.reverseOrder())
            .thenComparing(MoneyStoryCandidate::impact, Comparator.reverseOrder()).thenComparing(ORDER);

    public List<MoneyStoryCandidate> select(List<MoneyStoryCandidate> candidates) {
        List<MoneyStoryCandidate> selected = new ArrayList<>();
        for (var candidate : candidates.stream().sorted(DECK_ORDER).toList()) {
            if (selected.size() == 3) break;
            if (candidate.observation() != null && candidate.observation().kind() == MoneyStoryObservation.Kind.SUMMARY && !selected.isEmpty()) continue;
            if (selected.stream().noneMatch(existing -> repeats(existing, candidate))) selected.add(candidate);
        }
        return List.copyOf(selected);
    }

    private boolean repeats(MoneyStoryCandidate a, MoneyStoryCandidate b) {
        if (a.logicalKey().equals(b.logicalKey())) return true;
        if (a.observation() == null || b.observation() == null) {
            // A pattern and its simpler observation should not occupy two slots for the same subject.
            return a.type() == b.type() && Objects.equals(a.category(), b.category()) && Objects.equals(a.merchantId(), b.merchantId());
        }
        var left = a.observation(); var right = b.observation();
        if (left.noveltyKey().equals(right.noveltyKey())) return true;
        if (left.kind() == MoneyStoryObservation.Kind.CONTRIBUTOR && right.kind() == MoneyStoryObservation.Kind.CONTRIBUTOR) return true;
        var intersection = new HashSet<>(left.evidenceIds()); intersection.retainAll(right.evidenceIds());
        var union = new HashSet<>(left.evidenceIds()); union.addAll(right.evidenceIds());
        if (intersection.size() * 5 >= union.size() * 4) return true; // >=80% shared evidence
        if (left.kind() == MoneyStoryObservation.Kind.CONTRIBUTOR || right.kind() == MoneyStoryObservation.Kind.CONTRIBUTOR) {
            return !Collections.disjoint(left.focusTransactionIds(), right.focusTransactionIds());
        }
        return false;
    }
}
