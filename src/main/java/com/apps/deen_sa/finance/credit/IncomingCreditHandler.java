package com.apps.deen_sa.finance.credit;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.interpretation.EventFields;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.conversation.interpretation.StructuredEventHandler;
import com.apps.deen_sa.finance.legacy.mutation.StateMutationService;
import com.apps.deen_sa.finance.legacy.state.CompletenessLevelEnum;
import com.apps.deen_sa.finance.legacy.state.StateChangeEntity;
import com.apps.deen_sa.finance.legacy.state.StateChangeRepository;
import com.apps.deen_sa.finance.legacy.state.StateChangeTypeEnum;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Applies any incoming-money event to an asset-like destination account. */
@Service
@RequiredArgsConstructor
public class IncomingCreditHandler implements StructuredEventHandler {
    private static final List<String> CREDITABLE_TYPES = List.of("BANK_ACCOUNT", "CASH", "WALLET");

    private final StateChangeRepository stateChangeRepository;
    private final StateContainerService containerService;
    private final StateMutationService mutationService;
    private final AdjustmentCommandFactory commandFactory;

    @Override
    public String intentType() {
        return "INCOME";
    }

    @Override
    public SpeechResult handleSpeech(String text, ConversationContext context) {
        return SpeechResult.invalid("Incoming credits must be supplied through structured interpretation.");
    }

    @Override
    public SpeechResult handleFollowup(String answer, ConversationContext context) {
        return SpeechResult.invalid("Please state the amount and receiving account together.");
    }

    @Transactional
    @Override
    public SpeechResult handleInterpreted(EventPatch event, String rawText, ConversationContext context) {
        Map<String, Object> facts = mergedFacts(event.fields(), context);
        BigDecimal amount = decimal(facts.get("amount"));
        if (amount == null) {
            context.setActiveIntent(intentType());
            context.setWaitingForField("amount");
            context.setPartialObject(new LinkedHashMap<>(facts));
            return SpeechResult.followup("How much money did you receive?", List.of("amount"), facts);
        }

        String requestedDestination = string(facts.get("destinationAccount"));
        if (!destinationGrounded(requestedDestination, rawText)) requestedDestination = null;
        StateContainerEntity destination = resolveDestination(requestedDestination, context.getUserId());
        if (destination == null) {
            context.setActiveIntent(intentType());
            context.setWaitingForField("destinationAccount");
            context.setPartialObject(new LinkedHashMap<>(facts));
            return SpeechResult.followup(
                    "Which bank account, cash account, or wallet received this money?",
                    List.of("destinationAccount"), facts);
        }
        if (destination.getCurrentValue() == null) {
            return SpeechResult.invalid("I found " + destination.getName()
                    + ", but its current balance is unknown. Add its balance before recording credits.");
        }

        StateChangeEntity credit = new StateChangeEntity();
        credit.setUserId(String.valueOf(context.getUserId()));
        credit.setTransactionType(StateChangeTypeEnum.INCOME);
        credit.setAmount(amount);
        credit.setCategory(string(facts.get("category")) == null ? "Income" : string(facts.get("category")));
        credit.setSubcategory(string(facts.get("subcategory")));
        credit.setMainEntity(string(facts.get("merchantName")));
        credit.setTargetContainerId(destination.getId());
        LocalDate transactionDate = date(facts.get("transactionDate"));
        credit.setTimestamp(transactionDate == null ? Instant.now()
                : transactionDate.atStartOfDay(ZoneId.of(context.getTimezone())).toInstant());
        credit.setRawText(rawText);
        credit.setCompletenessLevel(CompletenessLevelEnum.FINANCIAL);
        credit.setNeedsEnrichment(false);
        credit.setFinanciallyApplied(false);

        StateChangeEntity saved = stateChangeRepository.save(credit);
        mutationService.apply(destination, commandFactory.forIncomingCredit(saved));
        saved.setFinanciallyApplied(true);
        stateChangeRepository.save(saved);
        context.reset();

        StateContainerEntity updated = containerService.findValueContainerById(destination.getId());
        return SpeechResult.builder()
                .status(SpeechStatus.SAVED)
                .message("Added ₹" + saved.getAmount().stripTrailingZeros().toPlainString() + " income to "
                        + updated.getName() + ". Balance is now ₹"
                        + updated.getCurrentValue().stripTrailingZeros().toPlainString() + ".")
                .savedEntity(saved)
                .needFollowup(false)
                .build();
    }

    private Map<String, Object> mergedFacts(EventFields fields, ConversationContext context) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (intentType().equals(context.getActiveIntent()) && context.getPartialObject() instanceof Map<?, ?> partial) {
            partial.forEach((key, value) -> merged.put(String.valueOf(key), value));
        }
        merged.putAll(fields.asMap());
        return merged;
    }

    private String string(Object value) { return value == null ? null : value.toString(); }
    private BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
    private LocalDate date(Object value) { return value == null ? null : value instanceof LocalDate date ? date : LocalDate.parse(value.toString()); }

    private StateContainerEntity resolveDestination(String requestedAccount, Long userId) {
        List<StateContainerEntity> eligible = containerService.getActiveContainers(userId).stream()
                .filter(account -> CREDITABLE_TYPES.contains(account.getContainerType()))
                .toList();
        if (requestedAccount == null || requestedAccount.isBlank()) {
            return null;
        }
        String requestedIdentity = accountIdentity(requestedAccount);
        List<StateContainerEntity> exact = eligible.stream()
                .filter(account -> accountIdentity(account.getName()).equals(requestedIdentity))
                .toList();
        if (exact.size() == 1) return exact.getFirst();
        if (!exact.isEmpty()) return null;
        String requested = normalize(requestedAccount);
        List<StateContainerEntity> matches = eligible.stream()
                .filter(account -> {
                    String name = normalize(account.getName());
                    return name.contains(requested) || requested.contains(name);
                }).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    static String accountIdentity(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:my|the)\\s+", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static boolean destinationGrounded(String requestedDestination, String currentMessage) {
        if (requestedDestination == null || requestedDestination.isBlank()
                || currentMessage == null || currentMessage.isBlank()) return false;
        String requested = accountIdentity(requestedDestination);
        String message = accountIdentity(currentMessage);
        return !requested.isBlank() && message.contains(requested);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("bank account", "")
                .replace("account", "")
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .trim();
    }
}
