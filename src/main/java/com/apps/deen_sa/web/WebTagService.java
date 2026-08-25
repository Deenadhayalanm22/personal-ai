package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.tag.TagEntity;
import com.apps.deen_sa.finance.tag.TagRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;

@Service
public class WebTagService {
    private final TagRepository tags;

    public WebTagService(TagRepository tags) {
        this.tags = tags;
    }

    @Transactional
    public TagItem create(AppUserEntity user, CreateTagRequest request) {
        String name = cleanName(request == null ? null : request.name());
        String normalized = name.toLowerCase(Locale.ROOT);
        if (tags.existsByUserIdAndNormalizedName(user.getId(), normalized)) throw duplicate();

        TagEntity tag = new TagEntity();
        tag.setUserId(user.getId());
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setCreatedAt(Instant.now());
        tag.setUpdatedAt(Instant.now());
        try {
            return item(tags.save(tag));
        } catch (DataIntegrityViolationException conflict) {
            throw duplicate();
        }
    }

    @Transactional(readOnly = true)
    public List<TagItem> list(AppUserEntity user) {
        return tags.findAllByUserIdOrderByNameAsc(user.getId()).stream().map(this::item)
                .sorted(Comparator.comparing(TagItem::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String cleanName(String value) {
        if (value == null || value.trim().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag name is required");
        String name = value.trim().replaceAll("\\s+", " ");
        if (name.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag name is too long");
        return name;
    }

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A tag with this name already exists");
    }

    private TagItem item(TagEntity value) {
        return new TagItem(value.getId(), value.getName());
    }

    public record CreateTagRequest(String name) { }
    public record TagItem(Long id, String name) { }
}
