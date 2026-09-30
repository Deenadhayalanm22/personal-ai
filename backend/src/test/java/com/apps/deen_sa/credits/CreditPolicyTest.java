package com.apps.deen_sa.credits;

import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.insights.ExpenseChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CreditPolicyTest {
    private CreditPolicy policy(String input,String cached,String output,boolean enabled) {
        return new CreditPolicy("test",new BigDecimal(input),new BigDecimal(cached),new BigDecimal(output),BigDecimal.TEN,BigDecimal.TEN,6,enabled);
    }
    @Test void exactTariffSeparatesCachedInputAndPreservesSmallUsage() {
        var tariff=policy("100","25","400",true);
        assertThat(tariff.cost(10000,4000,1000)).isEqualByComparingTo("1.1");
        assertThat(policy("0.1","0","0.2",true).cost(1,0,1)).isEqualByComparingTo("0.000001");
        assertThat(tariff.cost(0,0,0)).isZero();
        assertThatThrownBy(()->tariff.cost(10,11,0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void missingTariffAndGlobalPauseFailClosed() {
        assertThatThrownBy(()->policy("0","0","0",true).requireEnabled()).isInstanceOfSatisfying(WebApiException.class,e->assertThat(e.code()).isEqualTo("AI_CREDITS_NOT_CONFIGURED"));
        assertThatThrownBy(()->policy("100","25","400",false).requireEnabled()).isInstanceOfSatisfying(WebApiException.class,e->assertThat(e.code()).isEqualTo("AI_PAUSED"));
        assertThat(policy("100","101","400",true).configured()).isFalse();
    }
    @Test void unicodeAndLargeToolResultsAreIncludedInReservation() {
        var tariff=policy("100","25","400",true); var mapper=new ObjectMapper();
        var ascii=tariff.reservation(mapper,"system",List.of(new ExpenseChatModel.Message("user","a")),List.of());
        var unicode=tariff.reservation(mapper,"system",List.of(new ExpenseChatModel.Message("user","₹")),List.of());
        assertThat(unicode).isGreaterThan(ascii);
        assertThatThrownBy(()->tariff.reservation(mapper,"system",List.of(new ExpenseChatModel.Message("tool","x".repeat(120000))),List.of()))
                .isInstanceOfSatisfying(WebApiException.class,e->assertThat(e.code()).isEqualTo("AI_REQUEST_TOO_LARGE"));
    }
}
