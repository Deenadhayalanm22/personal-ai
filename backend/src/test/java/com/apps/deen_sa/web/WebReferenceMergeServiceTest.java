package com.apps.deen_sa.web;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.*;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.*;
import com.apps.deen_sa.service.WebReferenceMergeService;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebReferenceMergeServiceTest {
    @Test
    void rejectsDuplicateIds() {
        Harness h = new Harness();
        assertThatThrownBy(() -> h.service.merge(h.user, h.request(List.of(1L, 1L))))
                .isInstanceOf(WebApiException.class).hasMessage("At least two unique referenceIds are required");
    }

    @Test
    void rejectsMixedTypesAndForeignOwnership() {
        Harness mixed = new Harness();
        when(mixed.references.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(
                mixed.reference(1L, UserReferenceEntityType.MERCHANT, "One"),
                mixed.reference(2L, UserReferenceEntityType.ACCOUNT, "Two")));
        assertThatThrownBy(() -> mixed.service.merge(mixed.user, mixed.request(List.of(1L, 2L))))
                .isInstanceOfSatisfying(WebApiException.class, e -> assertThat(e.status().value()).isEqualTo(422));

        Harness foreign = new Harness();
        UserReferenceEntity other = foreign.reference(2L, UserReferenceEntityType.MERCHANT, "Two");
        AppUserEntity otherUser = new AppUserEntity(); otherUser.setId(99L); other.setUser(otherUser);
        when(foreign.references.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(
                foreign.reference(1L, UserReferenceEntityType.MERCHANT, "One"), other));
        assertThatThrownBy(() -> foreign.service.merge(foreign.user, foreign.request(List.of(1L, 2L))))
                .isInstanceOfSatisfying(WebApiException.class, e -> assertThat(e.status().value()).isEqualTo(403));
    }

    @Test
    void reassignsTransactionsCopiesAliasesAndMarksSourceMerged() {
        Harness h = new Harness();
        UserReferenceEntity canonical = h.reference(1L, UserReferenceEntityType.MERCHANT, "Old One");
        UserReferenceEntity source = h.reference(2L, UserReferenceEntityType.MERCHANT, "Old Two");
        FinancialTransactionEntity tx = new FinancialTransactionEntity(); tx.setMerchant(source);
        UserReferenceAliasEntity existing = new UserReferenceAliasEntity(); existing.setAliasText("Legacy");
        existing.setReferenceEntity(source);
        List<UserReferenceAliasEntity> saved = new ArrayList<>();
        when(h.references.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(canonical, source));
        when(h.references.findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(42L,
                UserReferenceEntityType.MERCHANT, "New Name")).thenReturn(Optional.empty());
        when(h.transactions.findByMerchantReferences(42L, List.of(1L, 2L)))
                .thenReturn(List.of(tx));
        when(h.aliases.findByReferenceEntityId(1L)).thenReturn(List.of());
        when(h.aliases.findByReferenceEntityId(2L)).thenReturn(List.of(existing));
        when(h.aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(anyLong(), anyString())).thenReturn(Optional.empty());
        when(h.aliases.save(any())).thenAnswer(call -> { saved.add(call.getArgument(0)); return call.getArgument(0); });
        when(h.aliases.findByReferenceEntityIdOrderByAliasTextAsc(1L)).thenAnswer(call -> saved);

        var result = h.service.merge(h.user, h.request(List.of(2L, 1L)));

        assertThat(result.updatedTransactionCount()).isEqualTo(1);
        assertThat(tx.getMerchant()).isSameAs(canonical);
        assertThat(source.isActive()).isFalse();
        assertThat(existing.getReferenceEntity()).isSameAs(canonical);
        assertThat(saved).extracting(UserReferenceAliasEntity::getAliasText)
                .containsExactlyInAnyOrder("Old One", "Old Two", "Legacy");
    }

    private static class Harness {
        final UserReferenceEntityRepository references = mock(UserReferenceEntityRepository.class);
        final UserReferenceAliasRepository aliases = mock(UserReferenceAliasRepository.class);
        final FinancialTransactionRepository transactions = mock(FinancialTransactionRepository.class);
        final WebReferenceMergeService service = new WebReferenceMergeService(references, aliases, transactions);
        final AppUserEntity user = user();
        WebReferenceMergeService.MergeRequest request(List<Long> ids) {
            return new WebReferenceMergeService.MergeRequest(UserReferenceEntityType.MERCHANT, ids, " New Name ");
        }
        UserReferenceEntity reference(long id, UserReferenceEntityType type, String name) {
            UserReferenceEntity r = new UserReferenceEntity(); r.setId(id); r.setUser(user); r.setEntityType(type);
            r.setCanonicalName(name); r.setActive(true); return r;
        }
        static AppUserEntity user() { AppUserEntity u = new AppUserEntity(); u.setId(42L); return u; }
    }
}
