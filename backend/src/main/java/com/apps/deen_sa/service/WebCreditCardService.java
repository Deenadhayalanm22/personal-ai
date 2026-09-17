package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserCreditCardEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserCreditCardRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

/** FIN-022 — user-configured statement cycles for named credit-card account references. */
@Service
public class WebCreditCardService {
    private final UserCreditCardRepository cards; private final UserReferenceEntityRepository references; private final MonthlyFinancialSnapshotService snapshots;
    public WebCreditCardService(UserCreditCardRepository cards, UserReferenceEntityRepository references, MonthlyFinancialSnapshotService snapshots) { this.cards = cards; this.references = references; this.snapshots = snapshots; }
    @Transactional(readOnly = true) public CardListResponse list(AppUserEntity user) { return new CardListResponse(cards.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId()).stream().map(this::response).toList()); }
    @Transactional public CardResponse create(AppUserEntity user, CardRequest request) { UserCreditCardEntity card = new UserCreditCardEntity(); card.setUser(user); apply(user, card, request, true); cards.saveAndFlush(card); snapshots.refreshCurrent(user); return response(card); }
    @Transactional public CardResponse update(AppUserEntity user, Long id, CardRequest request) { UserCreditCardEntity card = cards.findByIdAndUserId(id, user.getId()).orElseThrow(() -> notFound()); apply(user, card, request, false); cards.saveAndFlush(card); snapshots.refreshCurrent(user); return response(card); }
    private void apply(AppUserEntity user, UserCreditCardEntity card, CardRequest r, boolean create) {
        if (r == null) throw invalid("Credit-card details are required");
        if (r.accountReferenceId() != null) card.setAccountReference(references.findByIdAndUserIdAndEntityTypeAndActiveTrue(r.accountReferenceId(), user.getId(), UserReferenceEntityType.ACCOUNT).orElseThrow(() -> invalid("Choose an existing account reference for this card"))); else if (create) throw invalid("Choose the account used in your expense messages");
        if (r.cardName() != null) card.setCardName(text(r.cardName(), "Card name")); else if (create) throw invalid("Card name is required");
        if (r.issuerName() != null) card.setIssuerName(text(r.issuerName(), "Issuer")); else if (create) throw invalid("Issuer is required");
        if (r.statementDay() != null) card.setStatementDay(day(r.statementDay(), "Statement day")); else if (create) throw invalid("Statement day is required");
        if (r.dueDay() != null) card.setDueDay(day(r.dueDay(), "Due day")); else if (create) throw invalid("Due day is required");
        if (r.active() != null) card.setActive(r.active()); card.setUpdatedAt(Instant.now());
    }
    private String text(String value, String label) { if (value == null || value.trim().isEmpty() || value.trim().length() > 120) throw invalid(label + " must be 1 to 120 characters"); return value.trim(); }
    private int day(int value, String label) { if (value < 1 || value > 28) throw invalid(label + " must be between 1 and 28"); return value; }
    private CardResponse response(UserCreditCardEntity c) { return new CardResponse(c.getId(), c.getAccountReference().getId(), c.getAccountReference().getCanonicalName(), c.getCardName(), c.getIssuerName(), c.getStatementDay(), c.getDueDay(), c.isActive()); }
    private WebApiException invalid(String message) { return new WebApiException(HttpStatus.BAD_REQUEST, "INVALID_CREDIT_CARD", message); }
    private WebApiException notFound() { return new WebApiException(HttpStatus.NOT_FOUND, "CREDIT_CARD_NOT_FOUND", "Credit card not found"); }
    public record CardRequest(Long accountReferenceId, String cardName, String issuerName, Integer statementDay, Integer dueDay, Boolean active) { }
    public record CardResponse(Long id, Long accountReferenceId, String accountName, String cardName, String issuerName, int statementDay, int dueDay, boolean active) { }
    public record CardListResponse(List<CardResponse> cards) { }
}
