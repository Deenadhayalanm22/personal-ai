package com.apps.deen_sa.insights;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpenAiExpenseChatModelTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private HttpServer server;
    @AfterEach void stop() { if (server != null) server.stop(0); }
    private OpenAiExpenseChatModel model() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        var props = new ApplicationProperties(new ApplicationProperties.OpenAi("test-only", url, "gpt-4.1-mini", "gpt-4.1-mini", .5, "unused"), null);
        return new OpenAiExpenseChatModel(OpenAIOkHttpClient.builder().apiKey("test-only").baseUrl(url).build(), props, mapper, "gpt-4.1-mini");
    }
    @Test void actualSdkRoundTripIncludesToolSchemaArgumentsAndResults() throws Exception {
        var requests = new ArrayList<JsonNode>();
        String arguments = "{\"startDate\":\"2026-09-01\",\"endDate\":\"2026-10-01\",\"mode\":\"summary\",\"groupBy\":[],\"filters\":[],\"orderBy\":\"amount_desc\",\"limit\":10}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody()));
            Object message = requests.size() == 1
                    ? Map.of("role", "assistant", "tool_calls", List.of(Map.of("id", "call_1", "type", "function", "function", Map.of("name", "query_expenses", "arguments", arguments))))
                    : Map.of("role", "assistant", "content", "Your recorded spending is INR 750.");
            byte[] body = mapper.writeValueAsBytes(Map.of("id", "completion_1", "object", "chat.completion", "created", 1,
                    "model", "gpt-4.1-mini", "choices", List.of(Map.of("index", 0, "finish_reason", requests.size() == 1 ? "tool_calls" : "stop", "message", message))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var query = mock(ExpenseQueryTool.class);
        when(query.execute(any(), any())).thenReturn(new ExpenseQueryTool.Result(mapper.readValue(arguments, ExpenseQueryTool.Query.class), "INR", 2, new BigDecimal("750"), List.of(), false));
        var service = new ExpenseChatService(model(), new ExpenseMcpTools(query, mock(FinancialRecordsTool.class), mock(MonthlyPlanningTool.class), mapper), Clock.systemUTC());
        var user = new AppUserEntity(); user.setId(1L);
        var result = service.chat(user, new ExpenseChatService.Request("How much did I spend?", "2026-09", List.of()));
        assertThat(result.answer()).contains("750");
        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().path("store").asBoolean()).isFalse();
        assertThat(requests.getFirst().path("tools").get(0).path("function").path("parameters").path("properties").has("filters")).isTrue();
        var messages = requests.getLast().path("messages");
        assertThat(messages.get(messages.size() - 1).path("role").asText()).isEqualTo("tool");
        assertThat(messages.get(messages.size() - 1).path("content").asText()).contains("750");
    }
    @Test void providerFailureHasNoInternalDetailsAndNoAutomaticRetry() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            count.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close();
        });
        server.start();
        var tools = new ExpenseMcpTools(mock(ExpenseQueryTool.class), mock(FinancialRecordsTool.class), mock(MonthlyPlanningTool.class), mapper);
        assertThatThrownBy(() -> model().complete("system", List.of(new ExpenseChatModel.Message("user", "Hi")), tools.definitions()))
                .isInstanceOfSatisfying(WebApiException.class, ex -> assertThat(ex.code()).isEqualTo("CHAT_UNAVAILABLE"));
        assertThat(count.get()).isEqualTo(1);
    }
}
