package com.apps.deen_sa.cooking.coach;

import com.apps.deen_sa.cooking.session.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CookingSetupService {
    private static final BigDecimal RECOMMENDED_RICE = new BigDecimal("500");
    private static final Pattern QUANTITY = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|g|grm|grms|gram|grams)?", Pattern.CASE_INSENSITIVE);
    private final CookingSetupRepository repository;

    @Transactional
    public String begin(Long userId) {
        CookingSetupEntity setup = repository.findByUserId(userId).orElseGet(CookingSetupEntity::new);
        setup.setUserId(userId); setup.setStage(CookingSetupStage.CONFIRM_READY);
        setup.setChickenGrams(null); setup.setRiceGrams(null); setup.setRiceType(null); setup.setEquipment(null);
        repository.save(setup);
        return "Are you ready to prepare the chicken biryani? Reply Yes or No.";
    }

    public Optional<CookingSetupEntity> active(Long userId) { return repository.findByUserId(userId); }

    @Transactional
    public SetupReply answer(CookingSetupEntity setup, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        return switch (setup.getStage()) {
            case CONFIRM_READY -> confirmReady(setup, normalized);
            case CHICKEN_QUANTITY -> chickenQuantity(setup, text);
            case RICE_TYPE -> riceType(setup, normalized);
            case ALTER_RICE -> alterRice(setup, normalized);
            case RICE_QUANTITY -> riceQuantity(setup, text);
            case EQUIPMENT -> equipment(setup, normalized);
        };
    }

    private SetupReply confirmReady(CookingSetupEntity setup, String answer) {
        if (isYes(answer)) { setup.setStage(CookingSetupStage.CHICKEN_QUANTITY); return save(setup, "How much chicken do you have? Example: 600 g."); }
        if (isNo(answer)) { repository.delete(setup); return SetupReply.message("Okay. Say ‘start chicken biryani’ when you are ready."); }
        return SetupReply.message("Please reply Yes when you are ready, or No to stop setup.");
    }

    private SetupReply chickenQuantity(CookingSetupEntity setup, String text) {
        BigDecimal grams = quantity(text);
        if (!valid(grams)) return SetupReply.message("Tell me the chicken quantity in grams or kilograms, up to 5 kg. Example: 600 g.");
        setup.setChickenGrams(grams); setup.setStage(CookingSetupStage.RICE_TYPE);
        return save(setup, "Which rice are you using?\n• Basmati\n• Seeraga Samba\n• Other");
    }

    private SetupReply riceType(CookingSetupEntity setup, String answer) {
        if (!answer.contains("basmati"))
            return SetupReply.message("This internally tested V1 supports only long-grain Basmati rice. Seeraga Samba and other rice types need separate validation. Please reply Basmati to continue.");
        setup.setRiceType("BASMATI"); setup.setRiceGrams(RECOMMENDED_RICE); setup.setStage(CookingSetupStage.ALTER_RICE);
        String chicken = setup.getChickenGrams().stripTrailingZeros().toPlainString();
        return save(setup, "For " + chicken + " g chicken, this validated recipe recommends 500 g rice. Do you want to alter the rice quantity? Reply Yes or No.");
    }

    private SetupReply alterRice(CookingSetupEntity setup, String answer) {
        if (isNo(answer)) { setup.setStage(CookingSetupStage.EQUIPMENT); return save(setup, equipmentQuestion()); }
        if (isYes(answer)) { setup.setStage(CookingSetupStage.RICE_QUANTITY); return save(setup, "How much rice do you want to use? Example: 750 g."); }
        return SetupReply.message("Reply No to use the recommended 500 g rice, or Yes to enter a different rice quantity.");
    }

    private SetupReply riceQuantity(CookingSetupEntity setup, String text) {
        BigDecimal grams = quantity(text);
        if (!valid(grams)) return SetupReply.message("Tell me the rice quantity in grams or kilograms, up to 5 kg.");
        setup.setRiceGrams(grams); setup.setStage(CookingSetupStage.EQUIPMENT);
        return save(setup, equipmentQuestion());
    }

    private SetupReply equipment(CookingSetupEntity setup, String answer) {
        if (answer.contains("pressure") || answer.contains("cooker"))
            return SetupReply.message("Pressure-cooker timing is not validated for V1. Please choose Biryani pot to continue.");
        if (!answer.contains("biryani") && !answer.contains("briyani") && !answer.equals("pot"))
            return SetupReply.message("What are you cooking in?\n• Pressure cooker\n• Biryani pot");
        setup.setEquipment("BIRYANI_POT"); repository.save(setup);
        SetupSelection selection = new SetupSelection(setup.getChickenGrams(), setup.getRiceGrams(), setup.getRiceType(), setup.getEquipment());
        repository.delete(setup);
        return SetupReply.complete(selection);
    }

    private SetupReply save(CookingSetupEntity setup, String message) { repository.save(setup); return SetupReply.message(message); }
    private String equipmentQuestion() { return "What are you cooking in?\n• Pressure cooker\n• Biryani pot"; }
    private boolean isYes(String value) { return value.equals("yes") || value.equals("y") || value.equals("ready"); }
    private boolean isNo(String value) { return value.equals("no") || value.equals("n"); }
    private boolean valid(BigDecimal value) { return value != null && value.signum() > 0 && value.compareTo(new BigDecimal("5000")) <= 0; }
    private BigDecimal quantity(String text) {
        Matcher matcher = QUANTITY.matcher(text); if (!matcher.find()) return null;
        BigDecimal value = new BigDecimal(matcher.group(1));
        return "kg".equalsIgnoreCase(matcher.group(2)) ? value.multiply(new BigDecimal("1000")) : value;
    }

    public record SetupReply(String message, SetupSelection selection) {
        static SetupReply message(String value) { return new SetupReply(value, null); }
        static SetupReply complete(SetupSelection value) { return new SetupReply(null, value); }
        public boolean complete() { return selection != null; }
    }
    public record SetupSelection(BigDecimal chickenGrams, BigDecimal riceGrams, String riceType, String equipment) { }
}
