package com.apps.deen_sa.saree;

import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.extension.api.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class SareeJobWorkExtension implements BusinessExtension {
    private final List<EventCapability> events;

    public SareeJobWorkExtension(GenericLedgerService ledger, SareeLedgerQueryRepository queries,
                                 SareeWorkflowRepository workflows) {
        events = List.of(
                new SareeEventCapability(SareeEventCapability.MATERIAL_ISSUED, ledger, workflows),
                new SareeEventCapability(SareeEventCapability.PRODUCTION_SURRENDERED, ledger, workflows),
                new SareeEventCapability(SareeEventCapability.WAGE_STATEMENT_APPROVED, ledger, workflows),
                new SareeEventCapability(SareeEventCapability.WAGE_PAID, ledger, workflows),
                new SareeOperationalQueryCapability(queries));
    }

    @Override public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor("saree-job-work", "1.0.0", 1, "Saree Job Work", false,
                Set.of("en-IN", "ta-IN", "ta-Latn"),
                List.of("Give Selvi 1,000 metres of thread today", "Selvi surrendered 24 sarees",
                        "Yes", "Paid ₹2,400 cash today"));
    }

    @Override public Collection<EventCapability> events() { return events; }

    @Override public Collection<DeterministicEventRouter> deterministicRouters() {
        return List.of(text -> {
            String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            if (matches(value, "(?:give|gave|issue|issued|assign|assigned).*(?:thread|yarn|metres?|meters?|\\bm\\b)"))
                return Optional.of(SareeEventCapability.MATERIAL_ISSUED);
            if (matches(value, "(?:surrender|submitted|returned|produced).*(?:saree|piece)"))
                return Optional.of(SareeEventCapability.PRODUCTION_SURRENDERED);
            if (matches(value, "^(?:yes|yes[,.!]|ஆம்|aam)[.!]?$"))
                return Optional.of(SareeEventCapability.WAGE_STATEMENT_APPROVED);
            if (matches(value, "(?:pay|paid|pays).*(?:cash|bank|upi)"))
                return Optional.of(SareeEventCapability.WAGE_PAID);
            if (matches(value, "(?:₹|rs\\.?|inr)?\\s*[0-9][0-9,]*(?:\\.[0-9]+)?.*(?:cash|bank|upi)"))
                return Optional.of(SareeEventCapability.WAGE_PAID);
            if (matches(value, "(?:how much|show|what).*(?:thread|custody|wage.*payable|owe)"))
                return Optional.of("SAREE_OPERATION_QUERY");
            return Optional.empty();
        });
    }

    private static boolean matches(String text, String regex) { return Pattern.compile(regex).matcher(text).find(); }
}
