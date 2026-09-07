package com.apps.deen_sa.service;

import com.apps.deen_sa.conversation.AppUserEntity;
import com.apps.deen_sa.domain.UserReferenceEntityType;
import com.apps.deen_sa.entity.TransactionDraftEntity;
import com.apps.deen_sa.entity.TransactionDraftExtractionEntity;
import com.apps.deen_sa.entity.UserReferenceAliasEntity;
import com.apps.deen_sa.entity.UserReferenceEntity;
import com.apps.deen_sa.repository.UserReferenceAliasRepository;
import com.apps.deen_sa.repository.UserReferenceEntityRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfirmedAccountReferenceWriterTest {

    @Test
    void reusesAnExistingAccountReferenceForTheUser() {
        UserReferenceEntityRepository references = mock(UserReferenceEntityRepository.class);
        UserReferenceAliasRepository aliases = mock(UserReferenceAliasRepository.class);
        ConfirmedAccountReferenceWriter writer =
                new ConfirmedAccountReferenceWriter(references, aliases);

        AppUserEntity user = new AppUserEntity();
        user.setId(42L);
        TransactionDraftEntity draft = new TransactionDraftEntity();
        draft.setUser(user);
        TransactionDraftExtractionEntity extraction = new TransactionDraftExtractionEntity();
        extraction.setDraft(draft);
        extraction.setSourceAccountName(" HDFC Salary Account ");

        UserReferenceEntity account = new UserReferenceEntity();
        account.setId(7L);
        account.setEntityType(UserReferenceEntityType.ACCOUNT);
        account.setCanonicalName("HDFC Salary Account");
        when(references.findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                42L, UserReferenceEntityType.ACCOUNT, "HDFC Salary Account"))
                .thenReturn(Optional.of(account));
        when(aliases.findByReferenceEntityIdAndAliasTextIgnoreCase(
                7L, "HDFC Salary Account"))
                .thenReturn(Optional.of(new UserReferenceAliasEntity()));

        assertThat(writer.save(extraction)).isSameAs(account);
        verify(references).findByUserIdAndEntityTypeAndCanonicalNameIgnoreCase(
                42L, UserReferenceEntityType.ACCOUNT, "HDFC Salary Account");
    }
}
