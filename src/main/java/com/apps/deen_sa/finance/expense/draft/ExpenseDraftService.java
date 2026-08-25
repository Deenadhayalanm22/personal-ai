package com.apps.deen_sa.finance.expense.draft;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.ConversationSessionRepository;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.finance.expense.ExpenseTaxonomyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseDraftService {
    private final ExpenseDraftRepository drafts;
    private final ObjectMapper mapper;
    private final ExpenseTaxonomyRegistry taxonomy;
    private final ConversationSessionRepository sessions;

    public ExpenseDraftService(ExpenseDraftRepository drafts, ObjectMapper mapper,
                               ExpenseTaxonomyRegistry taxonomy, ConversationSessionRepository sessions) {
        this.drafts = drafts; this.mapper = mapper; this.taxonomy = taxonomy; this.sessions = sessions;
    }

    @Transactional
    public ExpenseDraftEntity capture(ExpenseDto dto, ConversationContext context, List<String> missingFields) {
        if (!"WHATSAPP".equalsIgnoreCase(context.getChannel())) return null;
        ExpenseDraftEntity draft = context.getActiveDraftId() == null ? null
                : drafts.findOwnedForUpdate(context.getActiveDraftId(), context.getUserId()).orElse(null);
        String messageId = metadataString(context, "inboundMessageId");
        if (draft == null && messageId != null)
            draft = drafts.findBySourceChannelAndSourceMessageId(context.getChannel(), messageId).orElse(null);
        Instant now = Instant.now();
        if (draft == null) {
            draft = new ExpenseDraftEntity();
            draft.setUserId(context.getUserId()); draft.setSourceChannel(context.getChannel());
            draft.setSourceMessageId(messageId); draft.setCreatedAt(now);
        } else if (draft.getStatus() != ExpenseDraftStatus.PENDING) {
            return draft;
        } else {
            draft.setVersion(draft.getVersion() + 1);
        }
        draft.setRawText(dto.getRawText() == null ? "" : dto.getRawText());
        draft.setPartialJson(mapper.convertValue(dto, Map.class));
        draft.setMissingFields(List.copyOf(missingFields));
        draft.setUpdatedAt(now);
        ExpenseDraftEntity saved = drafts.save(draft);
        context.setActiveDraftId(saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDraftEntity> pending(Long userId, int limit) {
        return drafts.findByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, ExpenseDraftStatus.PENDING, PageRequest.of(0, limit));
    }

    @Transactional
    public ExpenseDraftEntity update(Long userId, Long id, int expectedVersion, Map<String, Object> patch) {
        ExpenseDraftEntity draft = pendingForUpdate(userId, id, expectedVersion);
        Map<String, Object> values = new java.util.LinkedHashMap<>(draft.getPartialJson());
        if (patch != null) patch.forEach((key, value) -> {
            if (List.of("category", "subcategory", "sourceAccount", "merchantName", "transactionDate").contains(key))
                values.put(key, value);
        });
        String proposedCategory = string(values.get("category"));
        String proposedSubcategory = string(values.get("subcategory"));
        if (proposedCategory != null || proposedSubcategory != null) {
            String category = taxonomy.canonicalLabel(proposedCategory)
                    .filter(taxonomy::isCategory).orElseThrow(() -> new IllegalArgumentException("Select a valid category"));
            String subcategory = taxonomy.canonicalLabel(proposedSubcategory)
                    .filter(taxonomy.subcategoriesFor(category)::contains)
                    .orElseThrow(() -> new IllegalArgumentException("Select a valid subcategory for " + category));
            values.put("category", category); values.put("subcategory", subcategory);
        }
        ExpenseDto dto = mapper.convertValue(values, ExpenseDto.class);
        draft.setPartialJson(values); draft.setRawText(dto.getRawText() == null ? draft.getRawText() : dto.getRawText());
        draft.setMissingFields(com.apps.deen_sa.finance.expense.ExpenseValidator.findMissingFields(dto));
        draft.setVersion(draft.getVersion() + 1); draft.setUpdatedAt(Instant.now());
        ExpenseDraftEntity saved = drafts.save(draft);
        sessions.clearPendingDraft(userId, id);
        return saved;
    }

    @Transactional
    public ExpenseDto loadForConfirmation(Long userId, Long id, int expectedVersion) {
        ExpenseDraftEntity draft = pendingForUpdate(userId, id, expectedVersion);
        return mapper.convertValue(draft.getPartialJson(), ExpenseDto.class);
    }

    @Transactional
    public void complete(Long userId, Long id, Long transactionId) {
        ExpenseDraftEntity draft = drafts.findOwnedForUpdate(id, userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Draft not found"));
        if (draft.getStatus() != ExpenseDraftStatus.PENDING) return;
        draft.setStatus(ExpenseDraftStatus.COMPLETED); draft.setCompletedAt(Instant.now());
        draft.setCompletedTransactionId(transactionId); draft.setUpdatedAt(Instant.now());
        draft.setVersion(draft.getVersion() + 1); drafts.save(draft);
        sessions.clearPendingDraft(userId, id);
    }

    @Transactional
    public void discard(Long userId, Long id, int expectedVersion) {
        ExpenseDraftEntity draft = pendingForUpdate(userId, id, expectedVersion);
        draft.setStatus(ExpenseDraftStatus.DISCARDED); draft.setDiscardedAt(Instant.now());
        draft.setUpdatedAt(Instant.now()); draft.setVersion(draft.getVersion() + 1); drafts.save(draft);
        sessions.clearPendingDraft(userId, id);
    }

    @Transactional
    public void discardActive(Long userId, Long id) {
        if (id == null) return;
        ExpenseDraftEntity draft = drafts.findOwnedForUpdate(id, userId).orElse(null);
        if (draft == null || draft.getStatus() != ExpenseDraftStatus.PENDING) return;
        draft.setStatus(ExpenseDraftStatus.DISCARDED); draft.setDiscardedAt(Instant.now());
        draft.setUpdatedAt(Instant.now()); draft.setVersion(draft.getVersion() + 1); drafts.save(draft);
        sessions.clearPendingDraft(userId, id);
    }

    private ExpenseDraftEntity pendingForUpdate(Long userId, Long id, int version) {
        ExpenseDraftEntity draft = drafts.findOwnedForUpdate(id, userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Draft not found"));
        if (draft.getStatus() != ExpenseDraftStatus.PENDING)
            throw new IllegalStateException("Draft is no longer pending");
        if (draft.getVersion() != version)
            throw new OptimisticLockingFailureException("The draft changed since it was loaded");
        return draft;
    }

    private String metadataString(ConversationContext context, String key) {
        Object value = context.getMetadata() == null ? null : context.getMetadata().get(key);
        return value == null ? null : value.toString();
    }

    private String string(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    public ExpenseDto toDto(ExpenseDraftEntity draft) {
        return mapper.convertValue(draft.getPartialJson(), ExpenseDto.class);
    }
}
