package com.apps.deen_sa.conversation;

import org.springframework.stereotype.Component;

import java.util.Locale;

/** Deterministic, zero-token messages for routine conversation responses. */
@Component
public class ConversationMessages {
    public String gettingStarted(String locale) {
        if (isTamil(locale)) return """
                உங்கள் தினசரி செயல்பாடுகளை உரையாடல் மூலம் பதிவு செய்ய உதவுகிறேன்.

                என்ன நடந்தது, அளவு, அலகு மற்றும் தொடர்புடைய நபர் அல்லது பொருளை இயல்பாகச் சொல்லுங்கள்.
                """;
        return """
                I can help record operational activity through conversation.

                Describe what happened naturally, including any quantity, unit, person, or resource involved.
                """;
    }

    public String mutationNeedsClarification(String locale) {
        return isTamil(locale)
                ? "இதை புதிய செயல்பாடாகப் பதிவு செய்ய வேண்டுமா அல்லது ஏற்கனவே உள்ள பதிவுகளைக் காட்ட வேண்டுமா?"
                : "Should I record this as a new activity, or show existing records?";
    }

    public String unprocessed(String locale) {
        return isTamil(locale)
                ? "இந்த கோரிக்கையை என்னால் புரிந்துகொள்ள முடியவில்லை. மேம்படுத்தவும் பின்னர் பரிசீலிக்கவும் இந்த செய்தியை பதிவு செய்துள்ளோம். வேறு வார்த்தைகளில் மீண்டும் முயற்சிக்கலாம் அல்லது Help என தட்டச்சு செய்யலாம்."
                : "I couldn't understand that request. I've recorded this message for review so we can improve and follow up. Please try rephrasing it, or type Help to see what I can do.";
    }

    public String queryPeriodQuestion(String locale) {
        return isTamil(locale) ? "எந்த காலத்தை பார்க்க விரும்புகிறீர்கள் — இன்று, இந்த வாரம் அல்லது இந்த மாதம்?"
                : "Which period should I show — today, this week, or this month?";
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

}
