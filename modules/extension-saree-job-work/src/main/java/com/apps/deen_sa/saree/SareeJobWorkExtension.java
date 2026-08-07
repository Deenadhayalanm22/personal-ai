package com.apps.deen_sa.saree;

import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.extension.api.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class SareeJobWorkExtension implements BusinessExtension {
    private final List<EventCapability> events;

    public SareeJobWorkExtension(GenericLedgerService ledger, SareeLedgerQueryRepository queries) {
        events = List.of(
                new SareeEventCapability("SAREE_EMPLOYEE_REGISTERED", ledger),
                new SareeEventCapability("SAREE_MATERIAL_ISSUED", ledger),
                new SareeEventCapability("SAREE_PRODUCTION_SURRENDERED", ledger),
                new SareeEventCapability("SAREE_PRODUCTION_ACCEPTED", ledger),
                new SareeEventCapability("SAREE_WAGE_PAID", ledger),
                new SareeOperationalQueryCapability(queries));
    }

    @Override public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor("saree-job-work", "1.0.0", 1, "Saree Job Work", false,
                Set.of("en-IN", "ta-IN", "ta-Latn"),
                List.of("register employee Lakshmi", "issue 1000 metres thread to Lakshmi",
                        "Lakshmi surrendered 20 sarees", "accept 18 sarees from Lakshmi"));
    }
    @Override public Collection<EventCapability> events() { return events; }

    @Override public Collection<DeterministicEventRouter> deterministicRouters() {
        return List.of(text -> {
            String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
            if (matches(value, "(?:register|add|create)\\s+(?:an?\\s+)?employee")) return Optional.of("SAREE_EMPLOYEE_REGISTERED");
            if (matches(value, "(?:issue|gave|give).*(?:thread|yarn).*(?:metre|meter|m\\b)")) return Optional.of("SAREE_MATERIAL_ISSUED");
            if (matches(value, "(?:surrender|submitted|returned|produced).*(?:saree|piece)")) return Optional.of("SAREE_PRODUCTION_SURRENDERED");
            if (matches(value, "(?:accept|accepted|approve|approved).*(?:saree|piece)")) return Optional.of("SAREE_PRODUCTION_ACCEPTED");
            if (matches(value, "(?:pay|paid).*(?:wage|salary).*(?:saree|weav|employee|worker)")) return Optional.of("SAREE_WAGE_PAID");
            if (matches(value, "(?:how much|show|what).*(?:thread|custody|finished.*stock|wage.*payable|owe)")) return Optional.of("SAREE_OPERATION_QUERY");
            return Optional.empty();
        });
    }

    private static boolean matches(String text, String regex) { return Pattern.compile(regex).matcher(text).find(); }
}
