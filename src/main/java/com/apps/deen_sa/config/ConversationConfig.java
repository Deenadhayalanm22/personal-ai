package com.apps.deen_sa.config;

import com.apps.deen_sa.conversation.ConversationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversationConfig {

    @Bean
    public ConversationContext conversationContext() {
        return new ConversationContext();
    }
}
