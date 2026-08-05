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
        public Conversation {
            if (mode != null && !mode.isBlank()
                    && !java.util.Set.of("active", "shadow", "legacy").contains(mode.toLowerCase())) {
                throw new IllegalArgumentException("conversation.mode must be active, shadow, or legacy");
            }
        }
        public boolean active() { return "active".equalsIgnoreCase(mode); }
        public boolean shadow() { return "shadow".equalsIgnoreCase(mode); }
        public String effectiveMode() { return mode == null || mode.isBlank() ? "active" : mode.toLowerCase(); }
    }

    public record WhatsApp(
            String accessToken,
            String phoneNumberId,
            String apiBaseUrl
    ) {
    }
}
