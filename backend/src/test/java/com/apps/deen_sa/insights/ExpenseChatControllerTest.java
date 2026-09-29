package com.apps.deen_sa.insights;

import com.apps.deen_sa.controller.ExpenseChatController;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.exception.WebApiExceptionHandler;
import com.apps.deen_sa.service.WebAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.Cookie;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpenseChatControllerTest {
    private final WebAuthenticationService auth = mock(WebAuthenticationService.class);
    private final ExpenseChatService chat = mock(ExpenseChatService.class);
    private final ExpenseMcpTools tools = mock(ExpenseMcpTools.class);
    private final MoneyChatConversationStore conversations = mock(MoneyChatConversationStore.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new ExpenseChatController(auth, chat, tools, conversations, "http://localhost:4173", "http://localhost:4173"))
            .setControllerAdvice(new WebApiExceptionHandler()).build();
    @Test void rejectsUnauthenticatedChatAndMcpBeforeAnyDataOrModelCall() throws Exception {
        when(auth.authenticate(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(post("/api/web/expense-chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hi\",\"month\":\"2026-09\",\"history\":[]}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web/expense-chat/mcp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}")).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/web/expense-chat/conversations"))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/web/expense-chat/conversations/123e4567-e89b-12d3-a456-426614174000")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chat, tools, conversations);
    }
    @Test void usesResolvedProfileAndExposesMcpCatalog() throws Exception {
        var user = new AppUserEntity(); user.setId(99L);
        when(auth.authenticate("demo")).thenReturn(user);
        when(chat.chat(eq(user), any())).thenReturn(new ExpenseChatService.Response("Hello", List.of()));
        mvc.perform(post("/api/web/expense-chat").cookie(new Cookie("WEB_SESSION", "demo"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hi\",\"month\":\"2026-09\",\"history\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Hello"));
        when(tools.definitions()).thenReturn(List.of(new ObjectMapper().readTree("{\"name\":\"query_expenses\"}")));
        mvc.perform(post("/api/web/expense-chat/mcp").cookie(new Cookie("WEB_SESSION", "demo"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.tools[0].name").value("query_expenses"));
        when(conversations.list(99L)).thenReturn(List.of());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/web/expense-chat/conversations")
                .cookie(new Cookie("WEB_SESSION", "demo"))).andExpect(status().isOk());
        verify(conversations).list(99L);
        var id = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        var saved = new MoneyChatConversationStore.Conversation(id.toString(), "Hello", "2026-09", "",
                new ObjectMapper().readTree("[]"));
        when(conversations.put(eq(99L), eq(id), any())).thenReturn(saved);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/web/expense-chat/conversations/" + id)
                .cookie(new Cookie("WEB_SESSION", "demo")).contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(saved)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        verify(conversations).put(eq(99L), eq(id), any());
    }
    @Test void rejectsUntrustedOriginAndUnsupportedMcpVersion() throws Exception {
        when(auth.authenticate("s")).thenReturn(new AppUserEntity());
        for (String header : List.of("Origin", "MCP-Protocol-Version")) {
            mvc.perform(post("/api/web/expense-chat/mcp").cookie(new Cookie("WEB_SESSION", "s"))
                    .header(header, "untrusted").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                    .andExpect(status().is4xxClientError());
        }
        verifyNoInteractions(tools);
    }
}
