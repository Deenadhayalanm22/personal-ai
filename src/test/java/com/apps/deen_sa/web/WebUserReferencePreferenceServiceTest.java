package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.UserReferenceAliasEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebUserReferencePreferenceServiceTest {

    @Test
    void createsOneAliasRowForEachCommaSeparatedValue() {
        UserReferenceEntityRepository references = mock(UserReferenceEntityRepository.class);
        UserReferenceAliasRepository aliases = mock(UserReferenceAliasRepository.class);
        WebUserReferencePreferenceService service =
                new WebUserReferencePreferenceService(references, aliases);
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        when(references.findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                42L, UserReferenceEntityType.BENEFICIARY, "Jane Doe"))
                .thenReturn(Optional.empty());
        when(references.saveAndFlush(any())).thenAnswer(invocation -> {
            UserReferenceEntity saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(7L, "Jane"))
                .thenReturn(Optional.empty());
        when(aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(7L, "JD"))
                .thenReturn(Optional.empty());
        java.util.concurrent.atomic.AtomicLong aliasId =
                new java.util.concurrent.atomic.AtomicLong(8L);
        when(aliases.saveAndFlush(any())).thenAnswer(invocation -> {
            UserReferenceAliasEntity saved = invocation.getArgument(0);
            saved.setId(aliasId.incrementAndGet());
            return saved;
        });

        var result = service.create(user,
                new WebUserReferencePreferenceService.UserReferencePreferenceRequest(
                        UserReferenceEntityType.BENEFICIARY,
                        " Jane Doe ", " Jane, JD, jane, , "));

        assertThat(result.referenceId()).isEqualTo(7L);
        assertThat(result.entityType()).isEqualTo(UserReferenceEntityType.BENEFICIARY);
        assertThat(result.primaryReference()).isEqualTo("Jane Doe");
        assertThat(result.aliases())
                .extracting(WebUserReferencePreferenceService.AliasResponse::alias)
                .containsExactly("Jane", "JD");
        verify(references).saveAndFlush(any(UserReferenceEntity.class));
        verify(aliases, org.mockito.Mockito.times(2))
                .saveAndFlush(any(UserReferenceAliasEntity.class));
    }

    @Test
    void rejectsBlankPrimaryReference() {
        WebUserReferencePreferenceService service = new WebUserReferencePreferenceService(
                mock(UserReferenceEntityRepository.class),
                mock(UserReferenceAliasRepository.class));
        AppUserEntity user = new AppUserEntity();
        user.setId(42L);

        assertThatThrownBy(() -> service.create(user,
                new WebUserReferencePreferenceService.UserReferencePreferenceRequest(
                        UserReferenceEntityType.ACCOUNT, " ", "Salary")))
                .isInstanceOf(WebApiException.class)
                .hasMessage("Provide a primary reference");
    }
}
