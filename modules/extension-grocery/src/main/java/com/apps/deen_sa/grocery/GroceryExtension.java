package com.apps.deen_sa.grocery;

import com.apps.deen_sa.core.ledger.GenericLedgerService;
import com.apps.deen_sa.extension.api.*;
import org.springframework.stereotype.Component;

import java.util.*;

/** A deliberately small third extension proving that new business types require no core changes. */
@Component
public final class GroceryExtension implements BusinessExtension {
    private final List<EventCapability> events;

    public GroceryExtension(GenericLedgerService ledger) {
        events = List.of(new GroceryStockCapability("GROCERY_STOCK_RECEIVED", ledger),
                new GroceryStockCapability("GROCERY_SALE_RECORDED", ledger));
    }

    @Override public ExtensionDescriptor descriptor() {
        return new ExtensionDescriptor("grocery", "1.0.0", 1, "Grocery Inventory", false,
                Set.of("en-IN"), List.of("received 20 kg rice", "sold 3 kg rice"));
    }

    @Override public Collection<EventCapability> events() { return events; }

    @Override public Collection<DeterministicEventRouter> deterministicRouters() {
        return List.of(text -> {
            String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
            if (value.matches(".*\\b(received|purchased|restocked)\\b.*")) return Optional.of("GROCERY_STOCK_RECEIVED");
            if (value.matches(".*\\b(sold|sale)\\b.*")) return Optional.of("GROCERY_SALE_RECORDED");
            return Optional.empty();
        });
    }
}
