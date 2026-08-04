package com.apps.deen_sa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public record ApplicationProperties(
        OpenAi openai,
        WhatsApp whatsapp
) {

    public record OpenAi(
            String apiKey,
            String baseUrl
    ) {
    }

    public record WhatsApp(
            String accessToken,
            String phoneNumberId,
            String apiBaseUrl
    ) {
    }
}
