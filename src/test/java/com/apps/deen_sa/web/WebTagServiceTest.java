package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.finance.tag.TagEntity;
import com.apps.deen_sa.finance.tag.TagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebTagServiceTest {
    private final TagRepository tags = mock(TagRepository.class);
    private final WebTagService service = new WebTagService(tags);
    private final AppUserEntity user = user();

    @Test
    void createsNormalizedUserOwnedTag() {
        when(tags.save(any())).thenAnswer(invocation -> {
            TagEntity value = invocation.getArgument(0); value.setId(7L); return value;
        });

        var created = service.create(user, new WebTagService.CreateTagRequest("  Pondicherry   Trip "));

        assertThat(created).isEqualTo(new WebTagService.TagItem(7L, "Pondicherry Trip"));
        ArgumentCaptor<TagEntity> saved = ArgumentCaptor.forClass(TagEntity.class);
        verify(tags).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getNormalizedName()).isEqualTo("pondicherry trip");
    }

    @Test
    void rejectsCaseInsensitiveDuplicate() {
        when(tags.existsByUserIdAndNormalizedName(42L, "pondicherry")).thenReturn(true);

        assertThatThrownBy(() -> service.create(user, new WebTagService.CreateTagRequest("PONDICHERRY")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409 CONFLICT");
        verify(tags, never()).save(any());
    }

    @Test
    void listsOnlyAuthenticatedUsersTags() {
        TagEntity value = new TagEntity(); value.setId(3L); value.setName("Family"); value.setUserId(42L);
        when(tags.findAllByUserIdOrderByNameAsc(42L)).thenReturn(List.of(value));

        assertThat(service.list(user)).containsExactly(new WebTagService.TagItem(3L, "Family"));
    }

    private AppUserEntity user() {
        AppUserEntity value = new AppUserEntity(); value.setId(42L); return value;
    }
}
