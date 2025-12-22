package com.apps.deen_sa.handler;

import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.entity.ValueContainerEntity;
import com.apps.deen_sa.llm.impl.AccountSetupClassifier;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechHandler;
import com.apps.deen_sa.orchestrator.SpeechResult;
import com.apps.deen_sa.repo.ValueContainerRepo;
import com.apps.deen_sa.validator.AccountSetupValidator;
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
    private final ValueContainerRepo repo;

    public AccountSetupHandler(AccountSetupClassifier llm, ValueContainerRepo repo) {
        this.llm = llm;
        this.repo = repo;
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

        ValueContainerEntity saved = save(dto);
        ctx.reset();

        return SpeechResult.saved(saved);
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

        ValueContainerEntity saved = save(dto);
        ctx.reset();

        return SpeechResult.saved(saved);
    }

    private ValueContainerEntity save(AccountSetupDto dto) {

        ValueContainerEntity e = new ValueContainerEntity();

        e.setOwnerType(dto.getOwnerType() != null ? dto.getOwnerType() : "USER");
        e.setOwnerId(1L); // replace with auth user

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

        return repo.save(e);
    }

    public static String[] getNullPropertyNames(Object source) {

        final BeanWrapper src = new BeanWrapperImpl(source);

        return Arrays.stream(src.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .filter(name -> src.getPropertyValue(name) == null)
                .toArray(String[]::new);
    }
}
