package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.*;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.insights.WebExpenseCaptureService;
import com.apps.deen_sa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

/** FIN-EPIC-001: ordinary expense entry without model access or credit dependencies. */
@Service
@RequiredArgsConstructor
public class ManualExpenseCaptureService {
    private final TransactionDraftRepository drafts;
    private final TransactionDraftExtractionRepository extractions;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final UserReferenceEntityRepository references;
    private final UserReferenceAliasRepository aliases;
    private final Clock clock;

    public record Request(UUID requestId, LocalDate date, BigDecimal amount, String category,
                          String subcategory, String merchant, String account) {}

    @Transactional
    public WebExpenseCaptureService.Response prepare(AppUserEntity user, Request request) {
        if (request == null || request.requestId() == null || request.date() == null || request.amount() == null
                || request.date().isAfter(LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())))))
            throw invalid("Provide an amount and a past or current date.");
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.precision() - amount.scale() > 17)
            throw invalid("Enter a positive amount within the supported range.");
        String category = taxonomy.canonicalLabel(request.category()).orElse(null);
        String subcategory = taxonomy.canonicalLabel(request.subcategory()).orElse(null);
        if (taxonomy.spendingNatureFor(category, subcategory).isEmpty())
            throw invalid("Choose a valid category and subcategory.");
        String merchant = clean(request.merchant()), account = clean(request.account());
        // Raw normalized inputs are durable retry evidence; reference merges cannot change the fingerprint.
        String fingerprint = request.date() + " | " + amount + " | "
                + encode(category) + encode(subcategory) + encode(merchant) + encode(account);
        String evidence = "Manual expense\nAmount: " + amount + " " + user.getCurrency()
                + "\nDate: " + request.date() + "\nCategory: " + category + " / " + subcategory
                + (merchant == null ? "" : "\nMerchant: " + merchant)
                + (account == null ? "" : "\nAccount: " + account);
        String sourceId = "manual:" + user.getId() + ":" + request.requestId();
        int inserted = drafts.insertPendingIfAbsent(user.getId(), "TEXT", "WEB_APP", sourceId, evidence);
        var existing = drafts.findBySourceAndSourceMessageId(MessageSource.WEB_APP, sourceId).orElseThrow();
        var draft = drafts.findByIdForUpdate(existing.getId()).orElseThrow();
        if (inserted == 1) draft.setNormalizedText(fingerprint);
        if (!fingerprint.equals(draft.getNormalizedText()))
            throw new WebApiException(HttpStatus.CONFLICT, "MANUAL_REQUEST_CONFLICT", "This request ID already belongs to different expense details.");
        var extraction = extractions.findByDraftIdAndStatus(draft.getId(), TransactionDraftExtractionStatus.ACTIVE)
                .or(() -> extractions.findByDraftIdAndStatus(draft.getId(), TransactionDraftExtractionStatus.USED));
        if (extraction.isEmpty()) {
            if (draft.getStatus() != TransactionDraftStatus.PENDING)
                throw new WebApiException(HttpStatus.CONFLICT, "CAPTURE_CLOSED", "This preview has been cancelled.");
            var value = new TransactionDraftExtractionEntity();
            value.setDraft(draft); value.setAmount(amount); value.setOccurredAt(request.date());
            value.setCategoryId(category); value.setSubcategoryId(subcategory);
            value.setMerchantName(resolve(user.getId(), UserReferenceEntityType.MERCHANT, merchant));
            value.setSourceAccountName(resolve(user.getId(), UserReferenceEntityType.ACCOUNT, account));
            value.setStatus(TransactionDraftExtractionStatus.ACTIVE);
            extraction = Optional.of(extractions.saveAndFlush(value));
        }
        var value = extraction.orElseThrow();
        return new WebExpenseCaptureService.Response("READY", "Review this expense, then choose Record expense.", value.getId(),
                new WebExpenseCaptureService.Preview(value.getAmount(), value.getOccurredAt(), value.getCategoryId(),
                        value.getSubcategoryId(), value.getMerchantName(), value.getSourceAccountName(), user.getCurrency()));
    }
    private String resolve(Long owner, UserReferenceEntityType type, String name) {
        if (name == null) return null;
        var owned = references.findByUserIdAndEntityTypeAndActiveTrue(owner, type);
        // A full canonical selection takes precedence over aliases shared by other saved names.
        var canonical = owned.stream().filter(ref -> ref.getCanonicalName().equalsIgnoreCase(name)).findFirst();
        if (canonical.isPresent()) return canonical.get().getCanonicalName();
        var matches = owned.stream().filter(ref -> aliases.findByReferenceEntityId(ref.getId()).stream()
                .anyMatch(alias -> alias.getAliasText().equalsIgnoreCase(name))).toList();
        if (matches.size() > 1) throw new WebApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "CAPTURE_AMBIGUOUS_REFERENCE", "Choose the full saved merchant or account name.");
        return matches.isEmpty() ? name : matches.getFirst().getCanonicalName();
    }
    private static String encode(String value) { return value == null ? "-1:" : value.length() + ":" + value; }
    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.trim().length() > 200) throw invalid("Names must be at most 200 characters.");
        return value.trim();
    }
    private static WebApiException invalid(String message) {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_MANUAL_EXPENSE", message);
    }
}
