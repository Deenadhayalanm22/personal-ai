package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.entity.UserReferenceAliasEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConfirmedReferenceWriter {
    private static final String CONFIRMED_EXTRACTION = "CONFIRMED_EXTRACTION";

    private final UserReferenceEntityRepository references;
    private final UserReferenceAliasRepository aliases;

    public UserReferenceEntity save(
            TransactionDraftExtractionEntity extraction,
            UserReferenceEntityType type,
            String rawName) {
        String name = normalize(rawName);
        if (name == null) {
            return null;
        }

        Long userId = extraction.getDraft().getUser().getId();
        UserReferenceEntity reference = references
                .findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(userId, type, name)
                .orElseGet(() -> createReference(extraction, type, name));

        aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(reference.getId(), name)
                .orElseGet(() -> createAlias(reference, name));
        return reference;
    }

    private UserReferenceEntity createReference(
            TransactionDraftExtractionEntity extraction,
            UserReferenceEntityType type,
            String name) {
        Instant now = Instant.now();
        UserReferenceEntity reference = new UserReferenceEntity();
        reference.setUser(extraction.getDraft().getUser());
        reference.setEntityType(type);
        reference.setCanonicalName(name);
        reference.setActive(true);
        reference.setCreatedAt(now);
        reference.setUpdatedAt(now);
        return references.saveAndFlush(reference);
    }

    private UserReferenceAliasEntity createAlias(UserReferenceEntity reference, String name) {
        UserReferenceAliasEntity alias = new UserReferenceAliasEntity();
        alias.setReferenceEntity(reference);
        alias.setAliasText(name);
        alias.setSource(CONFIRMED_EXTRACTION);
        alias.setCreatedAt(Instant.now());
        return aliases.saveAndFlush(alias);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
