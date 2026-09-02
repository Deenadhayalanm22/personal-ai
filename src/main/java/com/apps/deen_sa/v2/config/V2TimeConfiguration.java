package com.apps.deen_sa.v2.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class V2TimeConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
