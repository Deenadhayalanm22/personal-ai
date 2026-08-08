package com.apps.deen_sa.extension.api;

import java.util.Optional;
import java.util.List;

/** Routes unambiguous domain syntax without an LLM. Empty means the model/router may continue. */
public interface DeterministicEventRouter {
    Optional<String> eventType(String text);
    default List<DeterministicEventCandidate> events(String text) { return List.of(); }
}
