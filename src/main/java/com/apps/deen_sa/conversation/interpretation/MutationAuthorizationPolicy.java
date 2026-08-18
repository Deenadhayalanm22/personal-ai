package com.apps.deen_sa.conversation.interpretation;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Language-independent boundary: history may aid interpretation but may never authorize a new mutation. */
@Component
public class MutationAuthorizationPolicy {
    private static final Pattern NUMBER = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(k|thousand|lakh|lac|crore|cr)?(?![\\p{L}\\p{N}])");

    public boolean isAuthorized(TurnInterpretation turn, String currentMessage) {
        if (turn.turnType() != TurnType.NEW_EVENT && turn.turnType() != TurnType.NEW_EVENTS) return true;
        for (EventPatch event : turn.events()) {
            Object rawValue = event.fields().asMap().get("rawText");
            String groundedRawInput = rawValue == null ? null : rawValue.toString();
            boolean rawInputIsGrounded = groundedRawInput != null && isGrounded(groundedRawInput, currentMessage);
            for (var fact : event.fields().asMap().entrySet()) {
                if (!(fact.getValue() instanceof Number)) continue;
                FieldEvidence evidence = event.evidence().stream()
                        .filter(item -> item != null && fact.getKey().equals(item.field())).findFirst().orElse(null);
                boolean separatelyGrounded = evidence != null && isGrounded(evidence.evidence(), currentMessage);
                boolean groundedByRawInput = rawInputIsGrounded
                        && isGrounded(String.valueOf(fact.getValue()), groundedRawInput);
                boolean numericValueGrounded = containsNumber(currentMessage, fact.getValue());
                if (!separatelyGrounded && !groundedByRawInput && !numericValueGrounded) return false;
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

    private boolean containsNumber(String message, Object expected) {
        if (message == null || expected == null) return false;
        BigDecimal target;
        try { target = new BigDecimal(expected.toString()).stripTrailingZeros(); }
        catch (NumberFormatException invalid) { return false; }
        Matcher matcher = NUMBER.matcher(Normalizer.normalize(message, Normalizer.Form.NFKC));
        while (matcher.find()) {
            try {
                BigDecimal value = new BigDecimal(matcher.group(1).replace(",", ""));
                value = value.multiply(multiplier(matcher.group(2))).stripTrailingZeros();
                if (value.compareTo(target) == 0) return true;
            } catch (NumberFormatException ignored) { }
        }
        return false;
    }

    private BigDecimal multiplier(String suffix) {
        if (suffix == null) return BigDecimal.ONE;
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k", "thousand" -> new BigDecimal("1000");
            case "lakh", "lac" -> new BigDecimal("100000");
            case "crore", "cr" -> new BigDecimal("10000000");
            default -> BigDecimal.ONE;
        };
    }
}
