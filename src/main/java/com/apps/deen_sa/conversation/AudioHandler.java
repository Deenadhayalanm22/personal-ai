package com.apps.deen_sa.conversation;

import com.openai.client.OpenAIClient;
import com.openai.core.MultipartField;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class AudioHandler implements AudioTranscriber {
    private final OpenAIClient openAIClient;
    private final String transcriptionModel;

    public AudioHandler(OpenAIClient openAIClient,
            @Value("${openai.transcription-model:gpt-4o-mini-transcribe}") String transcriptionModel) {
        this.openAIClient = openAIClient;
        this.transcriptionModel = transcriptionModel;
    }

    public String transcribe(byte[] audio, String mimeType) {
        MultipartField<InputStream> file = MultipartField.<InputStream>builder()
                .value(new ByteArrayInputStream(audio))
                .filename(fileNameFor(mimeType))
                .contentType(normalizeMimeType(mimeType))
                .build();

        TranscriptionCreateResponse response = openAIClient.audio().transcriptions().create(
                TranscriptionCreateParams.builder()
                        .file(file)
                        .model(transcriptionModel)
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
