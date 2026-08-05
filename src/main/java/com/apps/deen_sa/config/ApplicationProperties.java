package com.apps.deen_sa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public record ApplicationProperties(
        OpenAi openai,
        WhatsApp whatsapp,
        Conversation conversation
) {

    public record OpenAi(
            String apiKey,
            String baseUrl,
            String interpreterModel
    ) {
    }

    public record Conversation(String mode) {
        public boolean active() { return "active".equalsIgnoreCase(mode); }
        public boolean shadow() { return "shadow".equalsIgnoreCase(mode); }
    }

    public record WhatsApp(
            String accessToken,
            String phoneNumberId,
            String apiBaseUrl
    ) {
    }
}
