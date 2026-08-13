package com.apps.deen_sa.conversation;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class WhatsAppAccessCommandService {
    private static final Pattern ADD = Pattern.compile(
            "(?i)^\\s*(?:please\\s+)?(?:add|enable|allow)\\s+(?:(?:this\\s+)?(?:user|number)\\s+)?([+0-9][0-9\\s()-]{7,20})(?:\\s+as\\s+(?:a\\s+)?user)?\\s*[.!]?\\s*$");
    private static final Pattern REMOVE = Pattern.compile(
            "(?i)^\\s*(?:please\\s+)?(?:remove|disable|revoke|block)\\s+(?:(?:this\\s+)?(?:user|number)\\s+)?([+0-9][0-9\\s()-]{7,20})(?:\\s+as\\s+(?:a\\s+)?user)?\\s*[.!]?\\s*$");

    private final UserFeatureFlagService access;

    public WhatsAppAccessCommandService(UserFeatureFlagService access) {
        this.access = access;
    }

    public Optional<String> execute(String sender, String text) {
        if (!access.isSuperAdmin("WHATSAPP", sender)) return Optional.empty();
        Matcher add = ADD.matcher(text == null ? "" : text);
        if (add.matches()) {
            String number = access.normalizeExternalUserId("WHATSAPP", add.group(1));
            access.grantWhatsAppAccess(number);
            return Optional.of("Access enabled for +" + number + ".");
        }
        Matcher remove = REMOVE.matcher(text == null ? "" : text);
        if (remove.matches()) {
            String number = access.normalizeExternalUserId("WHATSAPP", remove.group(1));
            boolean removed = access.revokeWhatsAppAccess(number);
            return Optional.of(removed ? "Access removed for +" + number + "."
                    : "No user access was found for +" + number + ".");
        }
        return Optional.empty();
    }
}
