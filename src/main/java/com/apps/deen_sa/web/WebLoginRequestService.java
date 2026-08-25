package com.apps.deen_sa.web;

import com.apps.deen_sa.conversation.MagicLinkService;
import com.apps.deen_sa.conversation.UserFeatureFlagService;
import com.apps.deen_sa.conversation.WhatsAppReplySender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebLoginRequestService {
    private static final String CHANNEL = "WHATSAPP";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_REQUESTS_PER_WINDOW = 3;
    private static final String LOGIN_MESSAGE =
            "Use this secure link to sign in. It can be used once and expires shortly.";

    private final UserFeatureFlagService featureFlags;
    private final MagicLinkService magicLinks;
    private final WhatsAppReplySender replies;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public WebLoginRequestService(UserFeatureFlagService featureFlags, MagicLinkService magicLinks,
                                  WhatsAppReplySender replies) {
        this(featureFlags, magicLinks, replies, Clock.systemUTC());
    }

    WebLoginRequestService(UserFeatureFlagService featureFlags, MagicLinkService magicLinks,
                           WhatsAppReplySender replies, Clock clock) {
        this.featureFlags = featureFlags;
        this.magicLinks = magicLinks;
        this.replies = replies;
        this.clock = clock;
    }

    public void request(String phoneNumber, String remoteAddress) {
        String normalized = normalize(phoneNumber);
        String address = remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
        if (!normalized.matches("[0-9]{8,15}")) return;
        if (!allow("phone:" + normalized) || !allow("ip:" + address)) return;
        if (!featureFlags.hasAnyEnabledFeature(CHANNEL, normalized)) return;

        String link = magicLinks.generateForWhatsAppUser(normalized);
        replies.sendPortalLink(normalized, LOGIN_MESSAGE, link);
    }

    private String normalize(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9]", "");
    }

    private boolean allow(String key) {
        Instant now = clock.instant();
        Deque<Instant> values = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (values) {
            Instant cutoff = now.minus(RATE_LIMIT_WINDOW);
            while (!values.isEmpty() && !values.getFirst().isAfter(cutoff)) values.removeFirst();
            if (values.size() >= MAX_REQUESTS_PER_WINDOW) return false;
            values.addLast(now);
            return true;
        }
    }
}
