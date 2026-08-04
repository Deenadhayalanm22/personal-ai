package com.apps.deen_sa.conversation;

import com.apps.deen_sa.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppReplySender {

    private final RestTemplate restTemplate;
    private final ApplicationProperties properties;

    public void sendTextReply(String to, String message) {

        String url =
                properties.whatsapp().apiBaseUrl() + "/v19.0/"
                    + properties.whatsapp().phoneNumberId()
                        + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.whatsapp().accessToken());

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("body", message)
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Successfully pushed the message-{} to {}", message, to);
        } catch (Exception e) {
            // swallow or log — webhook flow must never break
            log.error("Failed to send WhatsApp reply to {}", to, e);
        }
    }
}
