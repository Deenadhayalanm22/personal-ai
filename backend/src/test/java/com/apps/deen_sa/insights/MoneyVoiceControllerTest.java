package com.apps.deen_sa.insights;
import com.apps.deen_sa.controller.MoneyVoiceController;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.exception.WebApiExceptionHandler;
import com.apps.deen_sa.service.WebAuthenticationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class MoneyVoiceControllerTest {
    final WebAuthenticationService auth = mock(WebAuthenticationService.class);
    final MoneyVoiceTranscriptionService voice = mock(MoneyVoiceTranscriptionService.class);
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MoneyVoiceController(auth,voice)).setControllerAdvice(new WebApiExceptionHandler()).build();
    @Test void rejectsUnauthenticatedAudioBeforeProvider() throws Exception {
        when(auth.authenticate(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(multipart("/api/web/expense-chat/transcribe").file(new MockMultipartFile("audio","q.webm","audio/webm",new byte[]{1}))).andExpect(status().isUnauthorized()); verifyNoInteractions(voice);
    }
    @Test void resolvesActiveProfileAndReturnsNoStoreTranscript() throws Exception {
        var user = new AppUserEntity(); user.setId(22L); when(auth.authenticate("demo")).thenReturn(user);
        when(voice.transcribe(eq(22L),any())).thenReturn(new MoneyVoiceTranscriptionService.Transcript("Where did my money go?"));
        mvc.perform(multipart("/api/web/expense-chat/transcribe").file(new MockMultipartFile("audio","q.webm","audio/webm",new byte[]{1})).cookie(new Cookie("WEB_SESSION","demo")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.text").value("Where did my money go?")); verify(voice).transcribe(eq(22L),any());
    }
}
