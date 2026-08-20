package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.finance.legacy.state.StateContainerEntity;
import com.apps.deen_sa.llm.impl.AccountSetupClassifier;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.finance.legacy.state.StateContainerRepository;
import com.apps.deen_sa.finance.legacy.state.StateContainerService;
import com.apps.deen_sa.finance.account.AccountSetupValidator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.beans.PropertyDescriptor;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AccountSetupHandler implements SpeechHandler {

    private final AccountSetupClassifier llm;
    private final StateContainerRepository repo;
    private final StateContainerService stateContainerService;
    private final DeterministicAccountSetupParser deterministicParser = new DeterministicAccountSetupParser();

    public AccountSetupHandler(AccountSetupClassifier llm, StateContainerRepository repo,
                               StateContainerService stateContainerService) {
        this.llm = llm;
        this.repo = repo;
        this.stateContainerService = stateContainerService;
    }

    @Override
    public String intentType() {
        return "ACCOUNT_SETUP";
    }

    @Override
    public SpeechResult handleSpeech(String text, ConversationContext ctx) {

        AccountSetupDto dto = deterministicParser.parse(text).orElseGet(() -> llm.extractAccount(text));
        dto.setRawText(text);

        List<String> missing = AccountSetupValidator.findMissingFields(dto);

        if (!missing.isEmpty()) {
            String next = missing.getFirst();

            ctx.setActiveIntent(intentType());
            ctx.setWaitingForField(next);
            ctx.setPartialObject(dto);

            return SpeechResult.followup(
                    llm.generateFollowupQuestion(next),
                    List.of(next),
                    dto
            );
        }

        applyProfileDefaults(dto);
        StateContainerEntity duplicate = findActiveDuplicate(dto, ctx.getUserId());
        if (duplicate != null) {
            ctx.reset();
            return duplicateAccount(duplicate);
        }
        StateContainerEntity saved = save(dto, ctx.getUserId());
        ctx.reset();
        return setupConfirmation(saved);
    }

    @Override
    public SpeechResult handleFollowup(String answer, ConversationContext ctx) {

        AccountSetupDto dto = (AccountSetupDto) ctx.getPartialObject();
        String missingField = ctx.getWaitingForField();

        AccountSetupDto refined =
                llm.extractFieldFromFollowup(dto, missingField, answer);

        Map<String, Object> mergedDetails = mergeDetails(dto.getDetails(), refined.getDetails());
        BeanUtils.copyProperties(refined, dto, getNullPropertyNames(refined));
        dto.setDetails(mergedDetails);
        dto.setRawText(dto.getRawText() + " " + answer);

        List<String> missing = AccountSetupValidator.findMissingFields(dto);

        if (!missing.isEmpty()) {
            String next = missing.getFirst();
            ctx.setWaitingForField(next);

            return SpeechResult.followup(
                    llm.generateFollowupQuestion(next),
                    List.of(next),
                    dto
            );
        }

        applyProfileDefaults(dto);
        StateContainerEntity duplicate = findActiveDuplicate(dto, ctx.getUserId());
        if (duplicate != null) {
            ctx.reset();
            return duplicateAccount(duplicate);
        }
        StateContainerEntity saved = save(dto, ctx.getUserId());
        ctx.reset();
        return setupConfirmation(saved);
    }

    private Map<String, Object> mergeDetails(Map<String, Object> existing, Map<String, Object> supplied) {
        if (existing == null && supplied == null) return null;
        Map<String, Object> merged = new HashMap<>();
        if (existing != null) merged.putAll(existing);
        if (supplied != null) merged.putAll(supplied);
        return merged;
    }

    private StateContainerEntity save(AccountSetupDto dto, Long userId) {

        StateContainerEntity e = new StateContainerEntity();

        e.setOwnerType(dto.getOwnerType() != null ? dto.getOwnerType() : "USER");
        e.setOwnerId(userId);

        e.setContainerType(dto.getContainerType());
        e.setName(dto.getName());
        e.setStatus("ACTIVE");

        e.setCurrency(dto.getCurrency());
        e.setCurrentValue(dto.getCurrentValue());
        e.setAvailableValue(dto.getAvailableValue());

        e.setCapacityLimit(dto.getCapacityLimit());
        e.setMinThreshold(dto.getMinThreshold());

        e.setExternalRefType(dto.getExternalRefType());
        e.setExternalRefId(dto.getExternalRefId());

        e.setOpenedAt(Instant.now());
        e.setDetails(dto.getDetails());

        StateContainerEntity saved = repo.save(e);
        stateContainerService.evictCache(userId);
        return saved;
    }

    private void applyProfileDefaults(AccountSetupDto dto) {
        if (dto.getCurrency() == null) dto.setCurrency("INR");
        if (dto.getAvailableValue() == null && dto.getCurrentValue() != null) {
            dto.setAvailableValue(dto.getCurrentValue());
        }
    }

    private StateContainerEntity findActiveDuplicate(AccountSetupDto dto, Long userId) {
        String identity = normalizeIdentity(dto.getName());
        return repo.findActiveByOwnerId(userId).stream()
                .filter(account -> account.getContainerType().equals(dto.getContainerType()))
                .filter(account -> normalizeIdentity(account.getName()).equals(identity))
                .findFirst()
                .orElse(null);
    }

    static String normalizeIdentity(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:my|the)\\s+", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private SpeechResult duplicateAccount(StateContainerEntity existing) {
        return SpeechResult.invalid(existing.getName()
                + " already exists. I did not create a duplicate account.");
    }

    private SpeechResult setupConfirmation(StateContainerEntity saved) {
        String balanceNote = saved.getCurrentValue() == null
                ? " I can track activity now; add a current balance later for exact balance insights."
                : " Opening balance recorded.";
        return SpeechResult.builder()
                .status(com.apps.deen_sa.conversation.SpeechStatus.SAVED)
                .message("Created " + saved.getName() + "." + balanceNote)
                .savedEntity(saved)
                .needFollowup(false)
                .build();
    }

    public static String[] getNullPropertyNames(Object source) {

        final BeanWrapper src = new BeanWrapperImpl(source);

        return Arrays.stream(src.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }
}
