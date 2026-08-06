package com.apps.deen_sa.conversation.interpretation;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/** Language-independent boundary: history may aid interpretation but may never authorize a new mutation. */
@Component
public class MutationAuthorizationPolicy {
    public boolean isAuthorized(TurnInterpretation turn, String currentMessage) {
        if (turn.turnType() != TurnType.NEW_EVENT && turn.turnType() != TurnType.NEW_EVENTS) return true;
        for (EventPatch event : turn.events()) {
            if (event.fields().amount() == null) continue;
            FieldEvidence amountEvidence = event.evidence().stream()
                    .filter(item -> item != null && "amount".equals(item.field()))
                    .findFirst().orElse(null);
            if (amountEvidence == null || !isGrounded(amountEvidence.evidence(), currentMessage)) return false;
        }
        return true;
    }

    private boolean isGrounded(String evidence, String message) {
        if (evidence == null || evidence.isBlank() || message == null) return false;
        return normalize(message).contains(normalize(evidence));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
