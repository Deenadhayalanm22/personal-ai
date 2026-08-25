package com.apps.deen_sa.finance.expense;

import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.dto.TaxonomyCandidateDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;

@Service
public class TaxonomyCandidateService {
    private final JdbcTemplate jdbc;
    private final ExpenseTaxonomyRegistry taxonomy;

    public TaxonomyCandidateService(JdbcTemplate jdbc, ExpenseTaxonomyRegistry taxonomy) {
        this.jdbc = jdbc;
        this.taxonomy = taxonomy;
    }

    @Transactional
    public void recordIfUseful(ExpenseDto expense, Long transactionId) {
        TaxonomyCandidateDto proposal = expense == null ? null : expense.getTaxonomyCandidate();
        if (proposal == null || transactionId == null || !usesFallback(expense.getSubcategory())) return;
        String category = clean(proposal.getCategory(), 100);
        String subcategory = clean(proposal.getSubcategory(), 100);
        if (category == null || subcategory == null || alreadyCanonical(category, subcategory)) return;

        String normalizedCategory = normalize(category);
        String normalizedSubcategory = normalize(subcategory);
        Instant now = Instant.now();
        Long candidateId = jdbc.queryForObject("""
                INSERT INTO taxonomy_candidate (
                    proposed_category, proposed_subcategory, normalized_category, normalized_subcategory,
                    status, occurrence_count, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (normalized_category, normalized_subcategory)
                DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
                RETURNING id
                """, Long.class, category, subcategory, normalizedCategory, normalizedSubcategory,
                Timestamp.from(now), Timestamp.from(now));

        int inserted = jdbc.update("""
                INSERT INTO taxonomy_candidate_occurrence
                    (candidate_id, transaction_id, item_concept, confidence, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (candidate_id, transaction_id) DO NOTHING
                """, candidateId, transactionId, clean(proposal.getItemConcept(), 150), confidence(proposal),
                Timestamp.from(now));
        if (inserted == 1) jdbc.update("""
                UPDATE taxonomy_candidate
                SET occurrence_count = occurrence_count + 1, last_seen_at = ?
                WHERE id = ?
                """, Timestamp.from(now), candidateId);
    }

    private boolean usesFallback(String subcategory) {
        return subcategory != null && (subcategory.equalsIgnoreCase("Others")
                || subcategory.toLowerCase(Locale.ROOT).startsWith("other "));
    }

    private boolean alreadyCanonical(String category, String subcategory) {
        String canonicalCategory = taxonomy.canonicalLabel(category).orElse(null);
        String canonicalSubcategory = taxonomy.canonicalLabel(subcategory).orElse(null);
        return canonicalCategory != null && canonicalSubcategory != null
                && taxonomy.subcategoriesFor(canonicalCategory).contains(canonicalSubcategory);
    }

    private BigDecimal confidence(TaxonomyCandidateDto proposal) {
        BigDecimal value = proposal.getConfidence();
        if (value == null) return null;
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private String clean(String value, int maxLength) {
        if (value == null) return null;
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) return null;
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
