package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.llm.impl.AccountSetupClassifier;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechHandler;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.core.state.StateContainerService;
import com.apps.deen_sa.finance.account.AccountSetupValidator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.beans.PropertyDescriptor;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
public class AccountSetupHandler implements SpeechHandler {

    private final AccountSetupClassifier llm;
    private final StateContainerRepository repo;
    private final StateContainerService stateContainerService;

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

        AccountSetupDto dto = llm.extractAccount(text);
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

        // merge (simple overwrite)
        BeanUtils.copyProperties(refined, dto, getNullPropertyNames(refined));
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
        StateContainerEntity saved = save(dto, ctx.getUserId());
        ctx.reset();
        return setupConfirmation(saved);
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
