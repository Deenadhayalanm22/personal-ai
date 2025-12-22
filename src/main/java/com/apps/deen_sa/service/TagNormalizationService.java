package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.TagMasterEntity;
import com.apps.deen_sa.llm.impl.TagSemanticMatcher;
import com.apps.deen_sa.repo.TagMasterRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TagNormalizationService {

    private final TagMasterRepository masterRepo;
    private final TagSemanticMatcher matcher;

    public TagNormalizationService(
            TagMasterRepository masterRepo, TagSemanticMatcher matcher) {
        this.masterRepo = masterRepo;
        this.matcher = matcher;
    }

    public List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }

        // 1️⃣ Load existing canonical tags
        List<String> existingCanonicalTags =
                masterRepo.findCanonicalTags();

        // 2️⃣ Ask LLM to semantically match
        Map<String, String> matches =
                matcher.match(existingCanonicalTags, rawTags);

        List<String> finalTags = new ArrayList<>();

        for (String raw : rawTags) {

            String cleaned = raw.toLowerCase().trim();
            String canonical = matches.get(cleaned);

            if (canonical != null) {
                finalTags.add(canonical);
            } else {
                // New meaning → create new canonical
                finalTags.add(cleaned);
                recordNewCanonical(cleaned);
            }
        }

        return finalTags.stream().distinct().toList();
    }

    private void recordNewCanonical(String tag) {
        if (masterRepo.findByCanonicalTagIgnoreCase(tag).isPresent()) return;

        TagMasterEntity master = new TagMasterEntity();
        master.setCanonicalTag(tag);
        master.setStatus("ACTIVE");

        masterRepo.save(master);
    }
}