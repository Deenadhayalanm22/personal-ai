package com.apps.deen_sa.finance.credit;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.conversation.interpretation.EventFields;
import com.apps.deen_sa.conversation.interpretation.EventPatch;
import com.apps.deen_sa.conversation.interpretation.StructuredEventHandler;
import com.apps.deen_sa.core.mutation.StateMutationService;
import com.apps.deen_sa.core.state.CompletenessLevelEnum;
import com.apps.deen_sa.core.state.StateChangeEntity;
import com.apps.deen_sa.core.state.StateChangeRepository;
import com.apps.deen_sa.core.state.StateChangeTypeEnum;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerService;
import com.apps.deen_sa.finance.account.strategy.AdjustmentCommandFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

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
        EventFields fields = event.fields();
        if (fields.amount() == null) {
            return SpeechResult.followup("How much money did you receive?", List.of("amount"), event);
        }

        StateContainerEntity destination = resolveDestination(fields.destinationAccount(), context.getUserId());
        if (destination == null) {
            return SpeechResult.followup(
                    "Which bank account, cash account, or wallet received this money?",
                    List.of("destinationAccount"), event);
        }
        if (destination.getCurrentValue() == null) {
            return SpeechResult.invalid("I found " + destination.getName()
                    + ", but its current balance is unknown. Add its balance before recording credits.");
        }

        StateChangeEntity credit = new StateChangeEntity();
        credit.setUserId(String.valueOf(context.getUserId()));
        credit.setTransactionType(StateChangeTypeEnum.INCOME);
        credit.setAmount(fields.amount());
        credit.setCategory(fields.category() == null ? "Income" : fields.category());
        credit.setSubcategory(fields.subcategory());
        credit.setMainEntity(fields.merchantName());
        credit.setTargetContainerId(destination.getId());
        credit.setTimestamp(fields.transactionDate() == null ? Instant.now()
                : fields.transactionDate().atStartOfDay(ZoneId.of(context.getTimezone())).toInstant());
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

    private StateContainerEntity resolveDestination(String requestedAccount, Long userId) {
        List<StateContainerEntity> eligible = containerService.getActiveContainers(userId).stream()
                .filter(account -> CREDITABLE_TYPES.contains(account.getContainerType()))
                .toList();
        if (requestedAccount == null || requestedAccount.isBlank()) {
            return eligible.size() == 1 ? eligible.getFirst() : null;
        }
        String requested = normalize(requestedAccount);
        List<StateContainerEntity> matches = eligible.stream()
                .filter(account -> {
                    String name = normalize(account.getName());
                    return name.contains(requested) || requested.contains(name);
                }).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace("bank account", "")
                .replace("account", "")
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .trim();
    }
}
