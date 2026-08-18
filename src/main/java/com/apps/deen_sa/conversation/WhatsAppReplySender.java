package com.apps.deen_sa.conversation;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.List;

@Service
@Log4j2
public class WhatsAppReplySender {

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final String phoneNumberId;
    private final String accessToken;

    public WhatsAppReplySender(RestTemplate restTemplate,
            @Value("${whatsapp.api-base-url:https://graph.facebook.com}") String apiBaseUrl,
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.access-token:}") String accessToken) {
        this.restTemplate = restTemplate; this.apiBaseUrl = apiBaseUrl;
        this.phoneNumberId = phoneNumberId; this.accessToken = accessToken;
    }

    public void sendTextReply(String to, String message) {

        sendPayload(to, message, Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("body", message)
        ));
    }

    /** Uploads media to Meta and sends it by media id. Returns false so callers can fall back to text. */
    public boolean sendImageReply(String to, ResponseMedia media, String caption) {
        try {
            String mediaId = upload(media);
            Map<String, Object> image = new java.util.HashMap<>();
            image.put("id", mediaId);
            if (caption != null && !caption.isBlank()) image.put("caption", limit(caption, 1024));
            sendPayloadOrThrow(to, "image", Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "image",
                    "image", image));
            return true;
        } catch (Exception e) {
            log.error("Failed to upload or send WhatsApp image to {}", to, e);
            return false;
        }
    }

    private String upload(ResponseMedia media) {
        String url = apiBaseUrl + "/v19.0/" + phoneNumberId + "/media";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(accessToken);

        ByteArrayResource file = new ByteArrayResource(media.content()) {
            @Override public String getFilename() { return media.filename(); }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");
        body.add("type", media.contentType());
        body.add("file", file);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
        Object id = response == null ? null : response.get("id");
        if (id == null || id.toString().isBlank()) throw new IllegalStateException("Meta media upload returned no id");
        return id.toString();
    }

    public void sendAudioConfirmation(String to, String transcription, String confirmationId) {
        String body = "I heard:\n\n" + limit(transcription, 850)
                + "\n\nShould I process this?";

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", body),
                        "action", Map.of("buttons", java.util.List.of(
                                replyButton("audio_confirm:" + confirmationId, "Yes"),
                                replyButton("audio_retry:" + confirmationId, "Retry")
                        ))
                )
        );

        sendPayload(to, "audio transcription confirmation", payload);
    }

    public void sendInteractiveReply(String to, String message, List<ResponseAction> actions) {
        if (actions.size() > 3) {
            sendListReply(to, message, actions);
            return;
        }
        List<Map<String, Object>> buttons = actions.stream()
                .map(action -> replyButton(limit(action.id(), 256), limit(action.title(), 20)))
                .toList();
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", limit(message, 1024)),
                        "action", Map.of("buttons", buttons)
                )
        );
        sendPayload(to, "interactive follow-up", payload);
    }

    private void sendListReply(String to, String message, List<ResponseAction> actions) {
        List<Map<String, Object>> rows = actions.stream().limit(10)
                .map(action -> Map.<String, Object>of(
                        "id", limit(action.id(), 200),
                        "title", limit(action.title(), 24)))
                .toList();
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "body", Map.of("text", limit(message, 1024)),
                        "action", Map.of(
                                "button", "Choose",
                                "sections", List.of(Map.of("title", "Options", "rows", rows))
                        )
                )
        );
        sendPayload(to, "interactive list follow-up", payload);
    }

    private Map<String, Object> replyButton(String id, String title) {
        return Map.of("type", "reply", "reply", Map.of("id", id, "title", title));
    }

    private String limit(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

    private void sendPayload(String to, String description, Map<String, Object> payload) {

        try {
            sendPayloadOrThrow(to, description, payload);
        } catch (Exception e) {
            // webhook flow must never break
            log.error("Failed to send WhatsApp reply to {}", to, e);
        }
    }

    private void sendPayloadOrThrow(String to, String description, Map<String, Object> payload) {

        String url =
                apiBaseUrl + "/v19.0/"
                    + phoneNumberId
                        + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(payload, headers);

        restTemplate.postForEntity(url, request, String.class);
        log.info("Successfully pushed {} to {}", description, to);
    }
}
