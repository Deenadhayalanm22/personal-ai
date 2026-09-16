package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.entity.UserIncomeProfileEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.repository.UserIncomeProfileRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebIncomeServiceTest {
    @Test void savesARangeWithoutRequestingExactSalary() {
        UserIncomeProfileRepository profiles=mock(UserIncomeProfileRepository.class);
        when(profiles.findById(42L)).thenReturn(java.util.Optional.empty());
        when(profiles.save(any())).thenAnswer(i->i.getArgument(0));
        WebIncomeService service=new WebIncomeService(profiles);
        var saved=service.saveProfile(user(),new WebIncomeService.ProfileRequest("RANGE","FROM_50000_TO_100000",null,"MONTHLY"));
        assertThat(saved.maskedValue()).isEqualTo("**");
    }
    @Test void rejectsExactModeWithoutAPositiveAmount() {
        WebIncomeService service=new WebIncomeService(mock(UserIncomeProfileRepository.class));
        assertThatThrownBy(()->service.saveProfile(user(),new WebIncomeService.ProfileRequest("EXACT",null,BigDecimal.ZERO,"MONTHLY")))
                .isInstanceOf(WebApiException.class).extracting(e->((WebApiException)e).code()).isEqualTo("INVALID_INCOME_OUTLOOK");
    }
    private AppUserEntity user(){AppUserEntity user=new AppUserEntity();user.setId(42L);return user;}
}
