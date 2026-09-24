package com.apps.deen_sa.whatsapp;

import com.apps.deen_sa.config.ApplicationProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.regex.Pattern;

/** Downloads WhatsApp media and sends it to the configured speech-to-text model. */
@Component
public class WhatsAppAudioTranscriber {
    private static final Pattern MEDIA_ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int MAX_AUDIO_BYTES = 16 * 1024 * 1024;
    private final RestTemplate http;
    private final ApplicationProperties properties;

    public WhatsAppAudioTranscriber(RestTemplate http, ApplicationProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public String transcribe(String mediaId) {
        if (mediaId == null || !MEDIA_ID.matcher(mediaId).matches())
            throw new IllegalArgumentException("Invalid WhatsApp media ID");
        var whatsapp = properties.whatsapp();
        var openai = properties.openai();
        if (whatsapp.accessToken() == null || whatsapp.accessToken().isBlank()
                || openai.apiKey() == null || openai.apiKey().isBlank())
            throw new IllegalStateException("Audio transcription credentials are not configured");

        HttpHeaders metaHeaders = new HttpHeaders();
        metaHeaders.setBearerAuth(whatsapp.accessToken());
        String metadataUrl = whatsapp.apiBaseUrl() + "/v19.0/" + mediaId;
        MediaMetadata metadata = http.exchange(metadataUrl, HttpMethod.GET,
                new HttpEntity<>(metaHeaders), MediaMetadata.class).getBody();
        if (metadata == null || metadata.url() == null) throw new IllegalStateException("WhatsApp media URL missing");
        URI mediaUrl = URI.create(metadata.url());
        String host = mediaUrl.getHost();
        if (!"https".equalsIgnoreCase(mediaUrl.getScheme()) || host == null
                || !(host.equals("fbcdn.net") || host.endsWith(".fbcdn.net")
                || host.equals("facebook.com") || host.endsWith(".facebook.com")
                || host.equals("fbsbx.com") || host.endsWith(".fbsbx.com")))
            throw new IllegalStateException("Unexpected WhatsApp media host");
        if (metadata.fileSize() != null && metadata.fileSize() > MAX_AUDIO_BYTES)
            throw new IllegalArgumentException("WhatsApp audio exceeds 16 MB");

        ResponseEntity<byte[]> download = http.exchange(mediaUrl, HttpMethod.GET,
                new HttpEntity<>(metaHeaders), byte[].class);
        byte[] audio = download.getBody();
        if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_BYTES)
            throw new IllegalStateException("WhatsApp audio is empty or too large");

        HttpHeaders audioHeaders = new HttpHeaders();
        audioHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        var resource = new ByteArrayResource(audio) {
            @Override public String getFilename() { return "voice.ogg"; }
        };
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("model", openai.transcriptionModel());
        parts.add("file", new HttpEntity<>(resource, audioHeaders));
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setBearerAuth(openai.apiKey());
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        Transcription response = http.postForObject(openai.baseUrl() + "/audio/transcriptions",
                new HttpEntity<>(parts, requestHeaders), Transcription.class);
        if (response == null || response.text() == null || response.text().isBlank())
            throw new IllegalStateException("Audio transcription returned no words");
        return response.text().trim();
    }

    record MediaMetadata(String url, @com.fasterxml.jackson.annotation.JsonProperty("file_size") Long fileSize) { }
    record Transcription(String text) { }
}
