package com.apps.deen_sa.conversation;

import com.apps.deen_sa.dto.ExpenseSummary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/** Deterministic, zero-token messages for routine conversation responses. */
@Component
public class ConversationMessages {
    public String gettingStarted(String locale) {
        if (isTamil(locale)) return """
                உங்கள் தினசரி வரவு செலவுகளை பதிவு செய்து, பணம் எங்கு செல்கிறது என்பதை அறிய உதவுகிறேன்.

                இயல்பாகவே செய்தி அனுப்பலாம். உதாரணம்:
                • மளிகைக்கு 500 செலவு செய்தேன்
                • நேற்று மின்சாரத்திற்கு 1,200 செலுத்தினேன்
                • 25,000 சம்பளம் வந்தது
                • இந்த மாதம் எவ்வளவு செலவு செய்தேன்?
                """;
        return """
                I can help you record everyday money activity and understand where your money goes.

                You can message me naturally. For example:
                • I spent 500 on groceries
                • Paid 1,200 for electricity yesterday
                • I received 25,000 salary
                • Add my bank account with a balance of 40,000
                • How much did I spend this month?

                To start, try sending: I spent 500 on groceries
                """;
    }

    public String mutationNeedsClarification(String locale) {
        return isTamil(locale)
                ? "இதை புதிய பணப் பதிவாகச் சேமிக்க வேண்டுமா அல்லது உங்கள் செலவுகளை காட்ட வேண்டுமா?"
                : "Should I record this as a new money activity, or show your existing records?";
    }

    public String queryPeriodQuestion(String locale) {
        return isTamil(locale) ? "எந்த காலத்திற்கான செலவை பார்க்க விரும்புகிறீர்கள் — இன்று, இந்த வாரம் அல்லது இந்த மாதம்?"
                : "Which period should I show — today, this week, or this month?";
    }

    public String summary(String locale, String period, ExpenseSummary summary) {
        BigDecimal total = summary.getTotalSpend() == null ? BigDecimal.ZERO : summary.getTotalSpend();
        String amount = "₹" + total.stripTrailingZeros().toPlainString();
        if (isTamil(locale)) return periodLabelTamil(period) + " உங்கள் மொத்த செலவு " + amount + ".";
        return "You spent a total of " + amount + " " + periodLabelEnglish(period) + ".";
    }

    public String skipped(String locale) {
        return isTamil(locale) ? "பரவாயில்லை — விடுபட்ட விவரத்தை பின்னர் சேர்க்கலாம்."
                : "No problem — I saved what you told me. You can add the missing detail later.";
    }

    public String cancelled(String locale) {
        return isTamil(locale) ? "சரி — கேள்விகளை நிறுத்திவிட்டேன். ஏற்கனவே பதிவான தகவல் பாதுகாப்பாக உள்ளது."
                : "Okay — I stopped the questions. Any activity already recorded is still saved.";
    }

    private boolean isTamil(String locale) {
        if (locale == null) return false;
        String normalized = locale.toLowerCase(Locale.ROOT);
        return normalized.equals("ta") || normalized.equals("ta-in");
    }

    private String periodLabelEnglish(String period) {
        return switch (period) {
            case "TODAY" -> "today";
            case "THIS_WEEK" -> "this week";
            case "THIS_MONTH" -> "this month";
            case "THIS_YEAR" -> "this year";
            default -> "for the requested period";
        };
    }

    private String periodLabelTamil(String period) {
        return switch (period) {
            case "TODAY" -> "இன்று";
            case "THIS_WEEK" -> "இந்த வாரம்";
            case "THIS_MONTH" -> "இந்த மாதம்";
            case "THIS_YEAR" -> "இந்த ஆண்டு";
            default -> "கேட்ட காலத்தில்";
        };
    }
}
