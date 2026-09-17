package com.apps.deen_sa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@Profile("!e2e")
public class TimeConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
