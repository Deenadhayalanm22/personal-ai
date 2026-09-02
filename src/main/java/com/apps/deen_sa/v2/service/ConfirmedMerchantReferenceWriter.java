package com.apps.deen_sa.v2.service;

import com.apps.deen_sa.v2.domain.UserReferenceEntityType;
import com.apps.deen_sa.v2.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.v2.entity.UserReferenceAliasEntity;
import com.apps.deen_sa.v2.entity.UserReferenceEntity;
import com.apps.deen_sa.v2.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.v2.repository.UserReferenceEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConfirmedMerchantReferenceWriter {
    private static final String CONFIRMED_EXTRACTION = "CONFIRMED_EXTRACTION";

    private final UserReferenceEntityRepository entityRepository;
    private final UserReferenceAliasRepository aliasRepository;

    public UserReferenceEntity save(TransactionDraftExtractionEntity extraction) {
        String merchant = normalized(extraction.getMerchantName());
        if (merchant == null) {
            return null;
        }

        Long userId = extraction.getDraft().getUser().getId();
        UserReferenceEntity reference = entityRepository
                .findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                        userId, UserReferenceEntityType.MERCHANT, merchant)
                .orElseGet(() -> createMerchant(extraction, merchant));

        aliasRepository.findByReferenceEntityIdAndAliasTextIgnoreCase(reference.getId(), merchant)
                .orElseGet(() -> createAlias(reference, merchant));
        return reference;
    }

    private UserReferenceEntity createMerchant(
            TransactionDraftExtractionEntity extraction,
            String merchant
    ) {
        UserReferenceEntity reference = new UserReferenceEntity();
        reference.setUser(extraction.getDraft().getUser());
        reference.setEntityType(UserReferenceEntityType.MERCHANT);
        reference.setCanonicalName(merchant);
        reference.setActive(true);
        reference.setCreatedAt(Instant.now());
        reference.setUpdatedAt(Instant.now());
        return entityRepository.saveAndFlush(reference);
    }

    private UserReferenceAliasEntity createAlias(
            UserReferenceEntity reference,
            String merchant
    ) {
        UserReferenceAliasEntity alias = new UserReferenceAliasEntity();
        alias.setReferenceEntity(reference);
        alias.setAliasText(merchant);
        alias.setSource(CONFIRMED_EXTRACTION);
        alias.setCreatedAt(Instant.now());
        return aliasRepository.saveAndFlush(alias);
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
