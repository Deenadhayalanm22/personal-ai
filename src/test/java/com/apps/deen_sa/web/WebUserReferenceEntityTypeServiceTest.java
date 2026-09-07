package com.apps.deen_sa.web;

import com.apps.deen_sa.domain.UserReferenceEntityType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebUserReferenceEntityTypeServiceTest {
    private final WebUserReferenceEntityTypeService service =
            new WebUserReferenceEntityTypeService();

    @Test
    void returnsAllUserReferenceEntityTypes() {
        assertThat(service.options().entityTypes())
                .containsExactly(
                        UserReferenceEntityType.MERCHANT,
                        UserReferenceEntityType.BENEFICIARY,
                        UserReferenceEntityType.ACCOUNT);
    }
}
