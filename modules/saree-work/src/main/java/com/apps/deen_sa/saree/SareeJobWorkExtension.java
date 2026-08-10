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

    @Override public String help(String locale) {
        if (locale != null && locale.toLowerCase(Locale.ROOT).startsWith("ta")) return """
                புடவை ஜாப்-வொர்க் செயல்பாடுகளை பதிவு செய்ய நான் உதவ முடியும்:
                • தொழிலாளருக்கு நூல் வழங்குதல்
                • முடிக்கப்பட்ட புடவைகளை சரண்டர் செய்தல்
                • வாராந்திர கூலியை உறுதிப்படுத்துதல்
                • பணம் அல்லது வங்கி மூலம் கூலி செலுத்துதல்
                • நூல் கையிருப்பு மற்றும் செலுத்த வேண்டிய கூலியை பார்க்குதல்

                உதாரணம்: “செல்விக்கு இன்று 1,000 மீட்டர் நூல் கொடுத்தேன்.”
                """;
        return """
                I can help with saree job-work operations:
                • Issue thread or yarn to a worker
                • Record surrendered or completed sarees
                • Review and approve weekly wages
                • Record wage payments by cash, bank, or UPI
                • Check thread custody and wages payable

                For example: “Give Selvi 1,000 metres of thread today.”
                """;
    }

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
