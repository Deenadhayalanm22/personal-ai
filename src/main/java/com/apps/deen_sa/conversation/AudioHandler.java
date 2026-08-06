package com.apps.deen_sa.conversation;

import com.apps.deen_sa.config.ApplicationProperties;
import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class AudioHandler {
    private final OpenAIClient openAIClient;
    private final ApplicationProperties properties;

    public String transcribe(byte[] audio, String mimeType) {
        MultipartField<InputStream> file = MultipartField.<InputStream>builder()
                .value(new ByteArrayInputStream(audio))
                .filename(fileNameFor(mimeType))
                .contentType(normalizeMimeType(mimeType))
                .build();

        TranscriptionCreateResponse response = openAIClient.audio().transcriptions().create(
                TranscriptionCreateParams.builder()
                        .file(file)
                        .model(properties.openai().transcriptionModel())
                        .build()
        );

        return response.transcription()
                .orElseThrow(() -> new IllegalStateException("OpenAI returned an unexpected transcription response"))
                .text();
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "audio/ogg";
        int parametersStart = mimeType.indexOf(';');
        return parametersStart >= 0 ? mimeType.substring(0, parametersStart) : mimeType;
    }

    private String fileNameFor(String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return switch (normalized) {
            case "audio/mpeg" -> "voice-note.mp3";
            case "audio/mp4", "audio/m4a" -> "voice-note.m4a";
            case "audio/wav", "audio/x-wav" -> "voice-note.wav";
            case "audio/webm" -> "voice-note.webm";
            default -> "voice-note.ogg";
        };
    }
}
