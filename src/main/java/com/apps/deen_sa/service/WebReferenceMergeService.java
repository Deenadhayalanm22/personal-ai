package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WebReferenceMergeService {
    private final UserReferenceEntityRepository references;
    private final UserReferenceAliasRepository aliases;
    private final FinancialTransactionRepository transactions;

    @Transactional
    public MergeResponse merge(AppUserEntity user, MergeRequest request) {
        ValidatedRequest valid = validate(request);
        List<UserReferenceEntity> selected = references.findAllByIdForUpdate(valid.referenceIds());
        if (selected.size() != valid.referenceIds().size()) throw error(HttpStatus.NOT_FOUND,
                "REFERENCE_NOT_FOUND", "One or more references were not found");
        if (selected.stream().anyMatch(r -> !r.getUser().getId().equals(user.getId()))) throw error(
                HttpStatus.FORBIDDEN, "REFERENCE_FORBIDDEN", "One or more references do not belong to the authenticated user");
        if (selected.stream().anyMatch(r -> r.getEntityType() != valid.entityType())) throw error(
                HttpStatus.UNPROCESSABLE_ENTITY, "MIXED_ENTITY_TYPES", "All references must have the requested entity type");

        Optional<UserReferenceEntity> named = references.findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                user.getId(), valid.entityType(), valid.canonicalName());
        UserReferenceEntity canonical = named.filter(UserReferenceEntity::isActive)
                .orElseGet(selected::getFirst);
        if (selected.stream().anyMatch(r -> !r.isActive())) throw error(
                HttpStatus.CONFLICT, "REFERENCE_NOT_ACTIVE", "All selected references must be active and unmerged");

        List<FinancialTransactionEntity> affected = matchingTransactions(user, valid);
        Instant now = Instant.now();
        for (FinancialTransactionEntity tx : affected) {
            if (valid.entityType() == UserReferenceEntityType.MERCHANT) tx.setMerchant(canonical);
            else if (valid.entityType() == UserReferenceEntityType.ACCOUNT) tx.setSourceAccount(canonical);
            tx.setUpdatedAt(now);
        }

        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        for (UserReferenceEntity source : selected) {
            addName(names, source.getCanonicalName());
        }
        for (String name : names.values()) {
            if (!name.equalsIgnoreCase(valid.canonicalName())
                    && aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(canonical.getId(), name).isEmpty()) {
                UserReferenceAliasEntity alias = new UserReferenceAliasEntity();
                alias.setReferenceEntity(canonical); alias.setAliasText(name); alias.setSource("WEB_MERGE");
                alias.setCreatedAt(now); aliases.save(alias);
            }
        }
        for (UserReferenceEntity source : selected) {
            if (source.getId().equals(canonical.getId())) continue;
            for (UserReferenceAliasEntity alias : aliases.findByReferenceEntityId(source.getId())) {
                if (aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(canonical.getId(), alias.getAliasText())
                        .isPresent()) {
                    aliases.delete(alias);
                } else {
                    alias.setReferenceEntity(canonical);
                    aliases.save(alias);
                }
            }
        }
        canonical.setCanonicalName(valid.canonicalName()); canonical.setActive(true); canonical.setUpdatedAt(now);
        for (UserReferenceEntity source : selected) if (!source.getId().equals(canonical.getId())) {
            source.setActive(false); source.setUpdatedAt(now);
        }
        return response(valid, canonical, selected, affected.size());
    }

    private MergeResponse response(ValidatedRequest request, UserReferenceEntity canonical,
            List<UserReferenceEntity> selected, long count) {
        List<String> aliasTexts = aliases.findByReferenceEntityIdOrderByAliasTextAsc(canonical.getId()).stream()
                .map(UserReferenceAliasEntity::getAliasText).toList();
        List<Long> mergedIds = selected.stream().map(UserReferenceEntity::getId)
                .filter(id -> !id.equals(canonical.getId())).sorted().toList();
        return new MergeResponse(mergeId(request), new CanonicalReference(canonical.getId(), canonical.getCanonicalName(),
                canonical.getEntityType(), aliasTexts), mergedIds, count, "COMPLETED");
    }

    private ValidatedRequest validate(MergeRequest request) {
        if (request == null || request.entityType() == null) throw badRequest("entityType is required");
        if (request.referenceIds() == null) throw badRequest("At least two referenceIds are required");
        LinkedHashSet<Long> ids = new LinkedHashSet<>(); request.referenceIds().stream().filter(Objects::nonNull).forEach(ids::add);
        if (ids.size() < 2) throw badRequest("At least two unique referenceIds are required");
        String name = request.canonicalName() == null ? "" : request.canonicalName().trim();
        if (name.isEmpty()) throw badRequest("canonicalName is required");
        if (name.length() > 255) throw badRequest("canonicalName must be at most 255 characters");
        return new ValidatedRequest(request.entityType(), ids.stream().sorted().toList(), name);
    }

    private String mergeId(ValidatedRequest request) {
        try {
            String raw = request.entityType() + "|" + request.canonicalName().toLowerCase(Locale.ROOT) + "|" + request.referenceIds();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
            return "merge_" + hash.substring(0, 26);
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private List<FinancialTransactionEntity> matchingTransactions(AppUserEntity user, ValidatedRequest request) {
        return switch (request.entityType()) {
            case MERCHANT -> transactions.findByMerchantReferences(user.getId(), request.referenceIds());
            case ACCOUNT -> transactions.findByAccountReferences(user.getId(), request.referenceIds());
            case BENEFICIARY -> List.of();
        };
    }
    private void addName(Map<String, String> names, String value) {
        if (value != null && !value.isBlank()) names.putIfAbsent(value.trim().toLowerCase(Locale.ROOT), value.trim());
    }
    private WebApiException badRequest(String message) { return error(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE_MERGE", message); }
    private WebApiException error(HttpStatus status, String code, String message) { return new WebApiException(status, code, message); }

    private record ValidatedRequest(UserReferenceEntityType entityType, List<Long> referenceIds, String canonicalName) { }
    public record MergeRequest(UserReferenceEntityType entityType, List<Long> referenceIds, String canonicalName) { }
    public record CanonicalReference(Long id, String name, UserReferenceEntityType entityType, List<String> aliases) { }
    public record MergeResponse(String mergeId, CanonicalReference canonicalReference,
            List<Long> mergedReferenceIds, long updatedTransactionCount, String status) { }
}
