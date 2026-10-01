package com.apps.deen_sa.insights;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.credits.CreditStore;
import com.apps.deen_sa.exception.WebApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** FIN-EPIC-003: bounded voice-to-text input; never answers or saves a question. */
@Service
public class MoneyVoiceTranscriptionService {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Map<String, String> FORMATS = Map.of(
            "audio/webm", "webm", "audio/mp4", "mp4", "audio/ogg", "ogg", "audio/wav", "wav");
    private final ApplicationProperties properties;
    private final CreditStore credits;
    private final RestTemplate http;
    private final Set<Long> active = new HashSet<>();
    private final Map<Long, Long> lastStarted = new HashMap<>();

    @Autowired
    public MoneyVoiceTranscriptionService(ApplicationProperties properties, CreditStore credits) {
        this(properties, credits, client());
    }
    MoneyVoiceTranscriptionService(ApplicationProperties properties, CreditStore credits, RestTemplate http) {
        this.properties = properties; this.credits = credits; this.http = http;
    }
    private static RestTemplate client() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(45000);
        return new RestTemplate(factory);
    }
    public Transcript transcribe(long userId, MultipartFile audio) {
        if (audio == null || audio.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "INVALID_VOICE_AUDIO", "Record a voice question first.");
        if (audio.getSize() > MAX_BYTES) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "VOICE_AUDIO_TOO_LARGE", "Record a shorter voice question (up to 8 MB).");
        String mime = audio.getContentType() == null ? "" : audio.getContentType().split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        String extension = FORMATS.get(mime);
        if (extension == null) throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "VOICE_AUDIO_FORMAT", "This recording format is not supported. Please type your question.");
        var config = properties.openai();
        if (config == null || config.apiKey() == null || config.apiKey().isBlank()
                || config.transcriptionModel() == null || config.transcriptionModel().isBlank())
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "VOICE_NOT_CONFIGURED", "Voice transcription is not configured yet. Please type your question.");
        var balance = credits.balance(userId);
        if (balance.paused()) throw error(HttpStatus.FORBIDDEN, "AI_ACCESS_PAUSED", "Your AI access is paused. Contact your administrator.");
        if (!balance.enabled() || !balance.configured()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "AI_CREDITS_NOT_CONFIGURED", "Money chat is currently unavailable. Please contact your administrator.");
        if (balance.available().signum() <= 0) throw error(HttpStatus.PAYMENT_REQUIRED, "AI_CREDITS_EXHAUSTED", "You’ve used your AI credits. Contact your administrator for more credits.");
        acquire(userId);
        try {
            byte[] bytes = audio.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw error(HttpStatus.BAD_REQUEST, "INVALID_VOICE_AUDIO", "Record a shorter voice question.");
            var resource = new ByteArrayResource(bytes) {
                @Override public String getFilename() { return "question." + extension; }
            };
            var parts = new LinkedMultiValueMap<String, Object>();
            parts.add("model", config.transcriptionModel());
            parts.add("response_format", "json");
            parts.add("prompt", "Transcribe the speaker's words in their natural language. Preserve Tamil, Hindi, Tanglish and mixed-language wording. Do not translate, answer the question or add words.");
            var fileHeaders = new HttpHeaders(); fileHeaders.setContentType(MediaType.parseMediaType(mime));
            parts.add("file", new HttpEntity<>(resource, fileHeaders));
            var headers = new HttpHeaders(); headers.setBearerAuth(config.apiKey()); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            Transcript result = http.postForObject(config.baseUrl() + "/audio/transcriptions", new HttpEntity<>(parts, headers), Transcript.class);
            if (result == null || result.text() == null || result.text().isBlank())
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "VOICE_NO_SPEECH", "No words were detected. Try recording again.");
            String text = result.text().trim();
            if (text.length() > 2000) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "VOICE_TRANSCRIPT_TOO_LONG", "That question is too long. Record a shorter question.");
            return new Transcript(text);
        } catch (WebApiException failure) { throw failure;
        } catch (Exception failure) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "VOICE_UNAVAILABLE", "Could not transcribe this recording. Try again or type your question.");
        } finally { release(userId); }
    }
    private synchronized void acquire(long userId) {
        long now = System.currentTimeMillis();
        lastStarted.entrySet().removeIf(entry -> entry.getValue() < now - 60_000 && !active.contains(entry.getKey()));
        if (active.size() >= 4 || active.contains(userId) || lastStarted.getOrDefault(userId, 0L) > now - 15_000)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "VOICE_BUSY", "Please wait a few seconds before transcribing another recording.");
        active.add(userId); lastStarted.put(userId, now);
    }
    private synchronized void release(long userId) { active.remove(userId); }
    private static WebApiException error(HttpStatus status, String code, String message) { return new WebApiException(status, code, message); }
    public record Transcript(String text) { }
}
