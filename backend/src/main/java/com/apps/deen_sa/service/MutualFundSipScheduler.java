package com.apps.deen_sa.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.investments.sip-scheduling-enabled", havingValue = "true")
public class MutualFundSipScheduler {
    private final WebMutualFundService mutualFunds;

    public MutualFundSipScheduler(WebMutualFundService mutualFunds) { this.mutualFunds = mutualFunds; }

    @Scheduled(cron = "${app.investments.sip-daily-cron:0 5 0 * * *}", zone = "Asia/Kolkata")
    public void createCurrentMonthOccurrences() { mutualFunds.createCurrentSipOccurrences(); }
}
