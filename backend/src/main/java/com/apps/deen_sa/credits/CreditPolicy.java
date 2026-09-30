package com.apps.deen_sa.credits;

import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.insights.ExpenseChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** FIN-EPIC-003: operator-configured credit tariff for the configured chat model. */
@Component
public record CreditPolicy(String model, BigDecimal inputRate, BigDecimal cachedRate, BigDecimal outputRate,
                           BigDecimal dailyLimit, BigDecimal requestLimit, int requestsPerMinute, boolean enabled) {
    public static final int OUTPUT_LIMIT = 2000;
    public CreditPolicy(
            @Value("${app.expense-chat.model:${openai.model}}") String model,
            @Value("${app.ai-credits.input-per-million:0}") BigDecimal inputRate,
            @Value("${app.ai-credits.cached-per-million:0}") BigDecimal cachedRate,
            @Value("${app.ai-credits.output-per-million:0}") BigDecimal outputRate,
            @Value("${app.ai-credits.daily-limit:0}") BigDecimal dailyLimit,
            @Value("${app.ai-credits.request-limit:10}") BigDecimal requestLimit,
            @Value("${app.ai-credits.requests-per-minute:6}") int requestsPerMinute,
            @Value("${app.ai-credits.enabled:true}") boolean enabled) {
        this.model = model; this.inputRate = inputRate; this.cachedRate = cachedRate; this.outputRate = outputRate;
        this.dailyLimit = dailyLimit; this.requestLimit = requestLimit;
        this.requestsPerMinute = requestsPerMinute; this.enabled = enabled;
    }
    public boolean configured() {
        return inputRate.signum() > 0 && outputRate.signum() > 0 && cachedRate.signum() >= 0
                && cachedRate.compareTo(inputRate) <= 0 && dailyLimit.signum() > 0
                && requestLimit.signum() > 0 && requestsPerMinute > 0;
    }
    public void requireEnabled() {
        if (!enabled) throw error(HttpStatus.SERVICE_UNAVAILABLE, "AI_PAUSED", "Money chat is temporarily paused.");
        if (!configured()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "AI_CREDITS_NOT_CONFIGURED", "Money chat credit rates and daily budget need administrator setup.");
    }
    public BigDecimal cost(long input, long cached, long output) {
        if (input < 0 || cached < 0 || cached > input || output < 0) throw new IllegalArgumentException("Invalid provider usage");
        return inputRate.multiply(BigDecimal.valueOf(input - cached))
                .add(cachedRate.multiply(BigDecimal.valueOf(cached)))
                .add(outputRate.multiply(BigDecimal.valueOf(output)))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.CEILING);
    }
    public BigDecimal reservation(ObjectMapper mapper, String system, List<ExpenseChatModel.Message> messages, Object tools) {
        try {
            // UTF-8 byte count bounds text tokens for the supported byte-level tokenizer.
            // Include serialized schemas/history and a generous allowance for provider framing.
            long inputBound = mapper.writeValueAsBytes(List.of(system, messages, tools)).length + 4096L + messages.size() * 256L;
            if (inputBound > 120_000) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "AI_REQUEST_TOO_LARGE", "Start a new chat or ask a more focused question.");
            return cost(inputBound, 0, OUTPUT_LIMIT);
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    public static WebApiException error(HttpStatus status, String code, String message) { return new WebApiException(status, code, message); }
}
