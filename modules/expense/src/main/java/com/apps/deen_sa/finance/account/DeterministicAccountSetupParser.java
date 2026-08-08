package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.AccountSetupDto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses explicit account declarations whose complete state is present in one message. */
final class DeterministicAccountSetupParser {
    private static final Pattern BANK = Pattern.compile(
            "(?i)^\\s*create\\s+my\\s+(.+?)\\s+bank\\s+account\\s+with\\s+(?:a\\s+)?current\\s+balance\\s+(?:of\\s+)?"
                    + "(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*[.!]?\\s*$");
    private static final Pattern CREDIT_CARD = Pattern.compile(
            "(?i)^\\s*create\\s+my\\s+(.+?)\\s+credit\\s+card\\s+with\\s+(?:a\\s+)?limit\\s+(?:of\\s+)?"
                    + "(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*,?\\s*"
                    + "(?:current\\s+)?outstanding\\s+(?:of\\s+)?(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"
                    + "\\s+and\\s+due\\s+day\\s+([0-9]{1,2})\\s*[.!]?\\s*$");

    Optional<AccountSetupDto> parse(String text) {
        if (text == null) return Optional.empty();
        Matcher bank = BANK.matcher(text);
        if (bank.matches()) return Optional.of(bank(bank.group(1), bank.group(2), text));
        Matcher card = CREDIT_CARD.matcher(text);
        if (card.matches()) return Optional.of(card(card.group(1), card.group(2), card.group(3), card.group(4), text));
        return Optional.empty();
    }

    private AccountSetupDto bank(String label, String balance, String rawText) {
        AccountSetupDto dto = base(label.trim() + " bank account", "BANK_ACCOUNT", rawText);
        dto.setCurrentValue(decimal(balance));
        return dto;
    }

    private AccountSetupDto card(String label, String limit, String outstanding, String dueDay, String rawText) {
        int day = Integer.parseInt(dueDay);
        if (day < 1 || day > 31) throw new IllegalArgumentException("Credit-card due day must be between 1 and 31");
        AccountSetupDto dto = base(label.trim() + " credit card", "CREDIT_CARD", rawText);
        dto.setCapacityLimit(decimal(limit));
        dto.setCurrentValue(decimal(outstanding));
        dto.setDetails(Map.of("dueDay", day));
        return dto;
    }

    private AccountSetupDto base(String name, String type, String rawText) {
        AccountSetupDto dto = new AccountSetupDto();
        dto.setValid(true); dto.setOwnerType("USER"); dto.setContainerType(type);
        dto.setName(name); dto.setCurrency("INR"); dto.setRawText(rawText);
        return dto;
    }

    private BigDecimal decimal(String value) { return new BigDecimal(value.replace(",", "")); }
}
