package com.apps.deen_sa.conversation;

import com.apps.deen_sa.config.ApplicationProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class WhatsAppMediaDownloader {

    private final RestTemplate restTemplate;
    private final ApplicationProperties properties;

    public byte[] download(String mediaId) {
        HttpEntity<Void> request = new HttpEntity<>(authorizationHeaders());
        String metadataUrl = properties.whatsapp().apiBaseUrl() + "/v19.0/" + mediaId;

        ResponseEntity<MediaMetadata> metadataResponse = restTemplate.exchange(
                metadataUrl, HttpMethod.GET, request, MediaMetadata.class);
        MediaMetadata metadata = metadataResponse.getBody();
        if (metadata == null || metadata.url() == null || metadata.url().isBlank()) {
            throw new IllegalStateException("WhatsApp did not return a media download URL");
        }

        ResponseEntity<byte[]> mediaResponse = restTemplate.exchange(
                metadata.url(), HttpMethod.GET, request, byte[].class);
        byte[] audio = mediaResponse.getBody();
        if (audio == null || audio.length == 0) {
            throw new IllegalStateException("Downloaded WhatsApp audio is empty");
        }
        return audio;
    }

    private HttpHeaders authorizationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.whatsapp().accessToken());
        return headers;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MediaMetadata(String url) {}
}
