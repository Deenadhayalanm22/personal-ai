package com.apps.deen_sa.extension.api;

import java.util.Optional;

/** Routes unambiguous domain syntax without an LLM. Empty means the model/router may continue. */
public interface DeterministicEventRouter {
    Optional<String> eventType(String text);
}
