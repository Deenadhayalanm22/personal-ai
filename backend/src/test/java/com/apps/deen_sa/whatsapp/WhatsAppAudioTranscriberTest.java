package com.apps.deen_sa.whatsapp;

import com.apps.deen_sa.config.ApplicationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsAppAudioTranscriberTest {
    @Test
    void downloadsMetaAudioAndTranscribesItWithConfiguredModel() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(http);
        var properties = new ApplicationProperties(
                new ApplicationProperties.OpenAi("openai-key", "https://api.openai.com/v1",
                        "unused", "unused", 0.55, "gpt-4o-mini-transcribe"),
                new ApplicationProperties.WhatsApp("meta-key", "phone-id", "https://graph.facebook.com"));
        server.expect(requestTo("https://graph.facebook.com/v19.0/12345"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer meta-key"))
                .andRespond(withSuccess("{\"url\":\"https://lookaside.fbsbx.com/audio/12345\",\"file_size\":4}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://lookaside.fbsbx.com/audio/12345"))
                .andExpect(header("Authorization", "Bearer meta-key"))
                .andRespond(withSuccess(new byte[]{1, 2, 3, 4}, MediaType.APPLICATION_OCTET_STREAM));
        server.expect(requestTo("https://api.openai.com/v1/audio/transcriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer openai-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("natural language")))
                .andRespond(withSuccess("{\"text\":\"Spent $250 at Swiggy\"}", MediaType.APPLICATION_JSON));

        assertThat(new WhatsAppAudioTranscriber(http, properties).transcribe("12345"))
                .isEqualTo("Spent 250 at Swiggy");
        server.verify();
    }
}
