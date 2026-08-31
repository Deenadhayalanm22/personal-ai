package com.apps.deen_sa.conversation.context;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.web.WebApiException;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PendingActionContextService {
    public static final String TYPE = "MISSING_TRANSACTION_DATE";
    private static final Pattern EXPLICIT_TEMPORAL_TEXT = Pattern.compile(
            "(?i)(?:^|[^\\p{L}])(?:today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday|"
                    + "\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?|"
                    + "\\d{1,2}\\s+(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|"
                    + "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?))"
                    + "(?:$|[^\\p{L}])");
    private final PendingActionContextRepository contexts;
    private final Duration expiry;
    private final String whatsappDestination;
    private final Clock clock;

    @Autowired
    public PendingActionContextService(PendingActionContextRepository contexts,
            @Value("${app.web.action-context-expiry:30m}") String expiry,
            @Value("${whatsapp.destination-number:}") String whatsappDestination) {
        this(contexts, DurationStyle.detectAndParse(expiry), whatsappDestination, Clock.systemUTC());
    }

    PendingActionContextService(PendingActionContextRepository contexts, Duration expiry,
                                  String whatsappDestination, Clock clock) {
        this.contexts = contexts;
        this.expiry = expiry;
        this.whatsappDestination = whatsappDestination;
        this.clock = clock;
    }

    @Transactional
    public ContextResponse create(Long userId, ContextRequest request) {
        if (request == null || !TYPE.equals(request.type()))
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT_TYPE",
                    "type must be MISSING_TRANSACTION_DATE");
        ZoneId zone = zone(request.timezone());
        LocalDate date = date(request.date());
        if (date.isAfter(LocalDate.now(clock.withZone(zone))))
            throw invalidDate();

        Instant now = clock.instant();
        contexts.findActiveForUpdate(userId).forEach(existing -> {
            existing.setStatus(PendingActionContextStatus.REPLACED);
            existing.setReplacedAt(now);
        });
        PendingActionContextEntity context = new PendingActionContextEntity();
        context.setId("ctx_" + UUID.randomUUID().toString().replace("-", ""));
        context.setUserId(userId);
        context.setContextType(TYPE);
        context.setContextValue(date.toString());
        context.setTimezone(zone.getId());
        context.setStatus(PendingActionContextStatus.ACTIVE);
        context.setCreatedAt(now);
        context.setExpiresAt(now.plus(expiry));
        contexts.save(context);
        return new ContextResponse(context.getId(), "ACTIVE", date.toString(), context.getExpiresAt(), whatsappUrl());
    }

    @Transactional(readOnly = true)
    public void attachToNextExpense(ExpenseDto expense, String rawText, Long userId, String channel) {
        if (expense == null || !"WHATSAPP".equalsIgnoreCase(channel)
                || expense.getPendingActionContextId() != null) return;
        contexts.findFirstByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                        userId, PendingActionContextStatus.ACTIVE, clock.instant())
                .ifPresent(context -> {
                    expense.setPendingActionContextId(context.getId());
                    boolean explicitDate = expense.getTransactionDate() != null || hasExplicitTemporalText(rawText);
                    if (!explicitDate) {
                        expense.setTransactionDate(LocalDate.parse(context.getContextValue()));
                        expense.setContextDateApplied(true);
                    }
                });
    }

    @Transactional
    public boolean consumeIfActive(Long userId, String contextId) {
        if (contextId == null) return false;
        PendingActionContextEntity context = contexts.findOwnedForUpdate(contextId, userId).orElse(null);
        Instant now = clock.instant();
        if (context == null || context.getStatus() != PendingActionContextStatus.ACTIVE
                || !context.getExpiresAt().isAfter(now)) return false;
        context.setStatus(PendingActionContextStatus.CONSUMED);
        context.setConsumedAt(now);
        return true;
    }

    public boolean hasExplicitTemporalText(String text) {
        return text != null && EXPLICIT_TEMPORAL_TEXT.matcher(text).find();
    }

    private ZoneId zone(String value) {
        try {
            if (value == null || value.isBlank()) throw new DateTimeException("missing timezone");
            return ZoneId.of(value);
        } catch (DateTimeException invalid) {
            throw new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE",
                    "The supplied timezone is not valid.");
        }
    }

    private LocalDate date(String value) {
        try {
            if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("", "", 0);
            return LocalDate.parse(value);
        } catch (DateTimeException invalid) {
            throw invalidDate();
        }
    }

    private WebApiException invalidDate() {
        return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTEXT_DATE",
                "Select a valid date that is not in the future.");
    }

    private String whatsappUrl() {
        String digits = whatsappDestination == null ? "" : whatsappDestination.replaceAll("\\D", "");
        return digits.isBlank() ? null : "https://wa.me/" + digits;
    }

    public record ContextRequest(String type, String date, String timezone) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextResponse(String contextId, String status, String date, Instant expiresAt,
                                  String whatsappUrl) { }
}
