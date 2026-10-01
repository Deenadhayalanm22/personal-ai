package com.apps.deen_sa.insights;
import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.credits.CreditStore;
import com.apps.deen_sa.exception.WebApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class MoneyVoiceTranscriptionServiceTest {
    final RestTemplate http = new RestTemplate();
    final MockRestServiceServer server = MockRestServiceServer.createServer(http);
    final CreditStore credits = mock(CreditStore.class);
    final ApplicationProperties properties = new ApplicationProperties(new ApplicationProperties.OpenAi("test-key", "https://provider.test/v1", "unused", "unused", .5, "gpt-4o-mini-transcribe"), null);
    final MoneyVoiceTranscriptionService service = new MoneyVoiceTranscriptionService(properties, credits, http);
    MockMultipartFile audio(String mime) { return new MockMultipartFile("audio", "untrusted.exe", mime, new byte[]{1,2,3}); }
    void allow(long id) { when(credits.balance(id)).thenReturn(new CreditStore.Balance(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, false, true, true)); }
    void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, String expected) { assertThatThrownBy(action).isInstanceOf(WebApiException.class).extracting(ex -> ((WebApiException) ex).code()).isEqualTo(expected); }
    @Test void transcribesConfiguredModelWithSafeFilenameAndNaturalLanguage() {
        allow(99);
        server.expect(requestTo("https://provider.test/v1/audio/transcriptions")).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(content().string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("question.mp4"), org.hamcrest.Matchers.containsString("gpt-4o-mini-transcribe"), org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("untrusted.exe")))))
            .andRespond(withSuccess("{\"text\":\"  இந்த மாதம் எவ்வளவு செலவு?  \"}", MediaType.APPLICATION_JSON));
        assertThat(service.transcribe(99, audio("audio/mp4;codecs=mp4a.40.2")).text()).isEqualTo("இந்த மாதம் எவ்வளவு செலவு?"); server.verify();
    }
    @Test void rejectsEmptyUnsupportedAndOversizedAudio() {
        code(() -> service.transcribe(1,null), "INVALID_VOICE_AUDIO");
        code(() -> service.transcribe(1,audio("text/plain")), "VOICE_AUDIO_FORMAT");
        code(() -> service.transcribe(1,new MockMultipartFile("audio","x.webm","audio/webm",new byte[MoneyVoiceTranscriptionService.MAX_BYTES+1])), "VOICE_AUDIO_TOO_LARGE");
        verifyNoInteractions(credits); server.verify();
    }
    @Test void rejectsPausedAndExhaustedAccess() {
        when(credits.balance(1)).thenReturn(new CreditStore.Balance(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN,true,true,true));
        code(() -> service.transcribe(1,audio("audio/webm")), "AI_ACCESS_PAUSED");
        when(credits.balance(2)).thenReturn(new CreditStore.Balance(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,false,true,true));
        code(() -> service.transcribe(2,audio("audio/webm")), "AI_CREDITS_EXHAUSTED"); server.verify();
    }
    @Test void missingConfigurationFailsBeforeProvider() {
        var missing = new ApplicationProperties(new ApplicationProperties.OpenAi("","x","x","x",.5,"x"),null);
        code(() -> new MoneyVoiceTranscriptionService(missing,credits,http).transcribe(1,audio("audio/ogg")), "VOICE_NOT_CONFIGURED"); verifyNoInteractions(credits);
    }
    @Test void rejectsSilenceAndLongTextAndSanitizesProviderFailure() {
        allow(1); allow(2); allow(3);
        server.expect(requestTo("https://provider.test/v1/audio/transcriptions")).andRespond(withSuccess("{\"text\":\" \"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v1/audio/transcriptions")).andRespond(withSuccess("{\"text\":\""+"x".repeat(2001)+"\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v1/audio/transcriptions")).andRespond(withServerError());
        code(() -> service.transcribe(1,audio("audio/webm")), "VOICE_NO_SPEECH");
        code(() -> service.transcribe(2,audio("audio/webm")), "VOICE_TRANSCRIPT_TOO_LONG");
        code(() -> service.transcribe(3,audio("audio/webm")), "VOICE_UNAVAILABLE"); server.verify();
    }
    @Test void limitsRepeatedUploadsButAllowsSeparateProfiles() {
        allow(1); allow(2);
        server.expect(requestTo("https://provider.test/v1/audio/transcriptions")).andRespond(withSuccess("{\"text\":\"Hello\"}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://provider.test/v1/audio/transcriptions")).andRespond(withSuccess("{\"text\":\"Hi\"}",MediaType.APPLICATION_JSON));
        service.transcribe(1,audio("audio/webm")); code(() -> service.transcribe(1,audio("audio/webm")), "VOICE_BUSY");
        assertThat(service.transcribe(2,audio("audio/webm")).text()).isEqualTo("Hi"); server.verify();
    }
    @Test void boundsGlobalAndProfileConcurrencyAndReleasesSlots() throws Exception {
        for (long id = 1; id <= 5; id++) allow(id);
        var entered = new java.util.concurrent.CountDownLatch(4);
        var release = new java.util.concurrent.CountDownLatch(1);
        var blockingHttp = mock(RestTemplate.class);
        when(blockingHttp.postForObject(anyString(), any(), eq(MoneyVoiceTranscriptionService.Transcript.class))).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout");
            return new MoneyVoiceTranscriptionService.Transcript("Question");
        });
        var limited = new MoneyVoiceTranscriptionService(properties, credits, blockingHttp);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<MoneyVoiceTranscriptionService.Transcript>>();
            try {
                for (long id = 1; id <= 4; id++) { final long owner = id; results.add(pool.submit(() -> limited.transcribe(owner, audio("audio/webm")))); }
                assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                code(() -> limited.transcribe(1, audio("audio/webm")), "VOICE_BUSY");
                code(() -> limited.transcribe(5, audio("audio/webm")), "VOICE_BUSY");
            } finally { release.countDown(); }
            for (var result : results) assertThat(result.get(3, java.util.concurrent.TimeUnit.SECONDS).text()).isEqualTo("Question");
            assertThat(limited.transcribe(5, audio("audio/webm")).text()).isEqualTo("Question");
        }
    }

}
