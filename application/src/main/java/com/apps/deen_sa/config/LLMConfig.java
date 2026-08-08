package com.apps.deen_sa.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {

    @Bean
    public OpenAIClient openAIClient(ApplicationProperties properties) {
        return OpenAIOkHttpClient.builder()
                .apiKey(properties.openai().apiKey())
                .baseUrl(properties.openai().baseUrl())
                .build();
    }
}
