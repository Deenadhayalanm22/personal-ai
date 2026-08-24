package com.apps.deen_sa.conversation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class MagicLinkService {
    private static final String WHATSAPP = "WHATSAPP";
    private static final int TOKEN_BYTES = 32;

    private final AppUserService users;
    private final MagicLinkRepository links;
    private final String baseUrl;
    private final Duration expiry;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public MagicLinkService(
            AppUserService users,
            MagicLinkRepository links,
            @Value("${app.web.base-url}") String baseUrl,
            @Value("${app.web.magic-link-expiry:15m}") String expiry) {
        this(users, links, baseUrl, DurationStyle.detectAndParse(expiry),
                new SecureRandom(), Clock.systemUTC());
    }

    MagicLinkService(AppUserService users, MagicLinkRepository links, String baseUrl,
                     Duration expiry, SecureRandom secureRandom, Clock clock) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("app.web.base-url must be configured");
        }
        if (expiry == null || expiry.isZero() || expiry.isNegative()) {
            throw new IllegalArgumentException("Magic-link expiry must be positive");
        }
        this.users = users;
        this.links = links;
        this.baseUrl = baseUrl;
        this.expiry = expiry;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    public String generateForWhatsAppUser(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("A WhatsApp phone number is required");
        }

        AppUserEntity user = users.resolve(WHATSAPP, phoneNumber.replaceAll("[^0-9]", ""));
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Instant now = clock.instant();
        MagicLinkEntity link = new MagicLinkEntity();
        link.setTokenHash(hash(token));
        link.setUserId(user.getId());
        link.setCreatedAt(now);
        link.setExpiresAt(now.plus(expiry));
        links.save(link);

        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/access")
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
