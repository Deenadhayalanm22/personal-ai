package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.UserReferenceAliasEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WebUserReferencePreferenceService {
    private static final String WEB_SOURCE = "WEB";

    private final UserReferenceEntityRepository entityRepository;
    private final UserReferenceAliasRepository aliasRepository;

    @Transactional(readOnly = true)
    public UserReferencePreferenceListResponse list(AppUserEntity user) {
        List<UserReferencePreferenceResponse> references = entityRepository
                .findByUserIdAndActiveTrueOrderByEntityTypeAscCanonicalNameAsc(user.getId())
                .stream()
                .map(reference -> new UserReferencePreferenceResponse(
                        reference.getId(),
                        reference.getEntityType(),
                        reference.getCanonicalName(),
                        aliasRepository
                                .findByReferenceEntityIdOrderByAliasTextAsc(reference.getId())
                                .stream()
                                .map(alias -> new AliasResponse(
                                        alias.getId(), alias.getAliasText()))
                                .toList()))
                .toList();
        return new UserReferencePreferenceListResponse(references);
    }

    @Transactional
    public UserReferencePreferenceResponse create(
            AppUserEntity user,
            UserReferencePreferenceRequest request
    ) {
        if (request == null || request.entityType() == null) {
            throw badRequest("Select a reference entity type");
        }
        String primaryReference = required(request.primaryReference(), "Provide a primary reference");
        List<String> aliasTexts = aliases(request.alias());

        UserReferenceEntity reference = entityRepository
                .findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                        user.getId(), request.entityType(), primaryReference)
                .map(this::activate)
                .orElseGet(() -> createReference(
                        user, request.entityType(), primaryReference));

        List<AliasResponse> savedAliases = aliasTexts.stream()
                .map(aliasText -> aliasRepository
                        .findByReferenceEntityIdAndAliasTextIgnoreCase(
                                reference.getId(), aliasText)
                        .orElseGet(() -> createAlias(reference, aliasText)))
                .map(alias -> new AliasResponse(alias.getId(), alias.getAliasText()))
                .toList();

        return new UserReferencePreferenceResponse(
                reference.getId(), reference.getEntityType(), reference.getCanonicalName(),
                savedAliases);
    }

    private UserReferenceEntity activate(UserReferenceEntity reference) {
        if (!reference.isActive()) {
            reference.setActive(true);
            reference.setUpdatedAt(Instant.now());
            return entityRepository.saveAndFlush(reference);
        }
        return reference;
    }

    private UserReferenceEntity createReference(
            AppUserEntity user,
            UserReferenceEntityType entityType,
            String primaryReference
    ) {
        Instant now = Instant.now();
        UserReferenceEntity reference = new UserReferenceEntity();
        reference.setUser(user);
        reference.setEntityType(entityType);
        reference.setCanonicalName(primaryReference);
        reference.setActive(true);
        reference.setCreatedAt(now);
        reference.setUpdatedAt(now);
        return entityRepository.saveAndFlush(reference);
    }

    private UserReferenceAliasEntity createAlias(
            UserReferenceEntity reference,
            String aliasText
    ) {
        UserReferenceAliasEntity alias = new UserReferenceAliasEntity();
        alias.setReferenceEntity(reference);
        alias.setAliasText(aliasText);
        alias.setSource(WEB_SOURCE);
        alias.setCreatedAt(Instant.now());
        return aliasRepository.saveAndFlush(alias);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw badRequest(message);
        }
        return value.trim();
    }

    private List<String> aliases(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("Provide at least one alias");
        }
        LinkedHashMap<String, String> uniqueAliases = new LinkedHashMap<>();
        for (String alias : value.split(",")) {
            String trimmed = alias.trim();
            if (!trimmed.isEmpty()) {
                uniqueAliases.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        if (uniqueAliases.isEmpty()) {
            throw badRequest("Provide at least one alias");
        }
        return List.copyOf(uniqueAliases.values());
    }

    private WebApiException badRequest(String message) {
        return new WebApiException(
                HttpStatus.BAD_REQUEST, "INVALID_REFERENCE_PREFERENCE", message);
    }

    public record UserReferencePreferenceRequest(
            UserReferenceEntityType entityType,
            String primaryReference,
            String alias) { }

    public record UserReferencePreferenceResponse(
            Long referenceId,
            UserReferenceEntityType entityType,
            String primaryReference,
            List<AliasResponse> aliases) { }

    public record UserReferencePreferenceListResponse(
            List<UserReferencePreferenceResponse> references) { }

    public record AliasResponse(Long aliasId, String alias) { }
}
