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
            Object rawValue = event.fields().asMap().get("rawText");
            String groundedRawInput = rawValue == null ? null : rawValue.toString();
            if (groundedRawInput != null && !isGrounded(groundedRawInput, currentMessage)) return false;
            for (var fact : event.fields().asMap().entrySet()) {
                if (!(fact.getValue() instanceof Number)) continue;
                FieldEvidence evidence = event.evidence().stream()
                        .filter(item -> item != null && fact.getKey().equals(item.field())).findFirst().orElse(null);
                boolean separatelyGrounded = evidence != null && isGrounded(evidence.evidence(), currentMessage);
                boolean groundedByRawInput = groundedRawInput != null
                        && isGrounded(String.valueOf(fact.getValue()), groundedRawInput);
                if (!separatelyGrounded && !groundedByRawInput) return false;
            }
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
