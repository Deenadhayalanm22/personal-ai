package com.apps.deen_sa.service;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class CaptureReferenceMergeTest {
    @Test void mergedPreviewNameResolvesToActiveAliasBeforeRecording(){
        var refs=mock(UserReferenceEntityRepository.class);var aliases=mock(UserReferenceAliasRepository.class);
        var user=new AppUserEntity();user.setId(42L);var draft=new TransactionDraftEntity();draft.setUser(user);
        var extraction=new TransactionDraftExtractionEntity();extraction.setDraft(draft);
        var active=new UserReferenceEntity();active.setId(8L);active.setCanonicalName("Saravana Bhavan");active.setActive(true);
        var alias=new UserReferenceAliasEntity();alias.setAliasText("SB");
        when(refs.findByUserIdAndEntityTypeAndActiveTrue(42L,UserReferenceEntityType.MERCHANT)).thenReturn(List.of(active));
        when(aliases.findByReferenceEntityId(8L)).thenReturn(List.of(alias));
        when(aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(8L,"SB")).thenReturn(Optional.of(alias));
        assertThat(new ConfirmedReferenceWriter(refs,aliases).save(extraction,UserReferenceEntityType.MERCHANT,"SB")).isSameAs(active);
        verify(refs,never()).saveAndFlush(any());
    }
}
