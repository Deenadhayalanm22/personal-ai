package com.apps.deen_sa.insights;

import com.apps.deen_sa.config.ApplicationProperties;
import com.apps.deen_sa.exception.WebApiException;
import com.apps.deen_sa.llm.AiCallTelemetry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.core.RequestOptions;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/** FIN-EPIC-003: model-selected expense tools, without an intent classifier. */
@Service
public class OpenAiExpenseChatModel implements ExpenseChatModel {
    private final OpenAIClient client;
    private final ApplicationProperties properties;
    private final ObjectMapper mapper;
    private final String model;

    public OpenAiExpenseChatModel(OpenAIClient client, ApplicationProperties properties, ObjectMapper mapper,
                                  @Value("${app.expense-chat.model:${openai.model}}") String model) {
        this.client = client.withOptions(options -> options.maxRetries(0));
        this.properties = properties;
        this.mapper = mapper;
        this.model = model;
    }

    @Override
    public Reply complete(String system, List<Message> messages, List<JsonNode> tools) {
        if (properties.openai().apiKey() == null || properties.openai().apiKey().isBlank())
            throw new WebApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_NOT_CONFIGURED", "Money chat is not configured yet.");
        var builder = ChatCompletionCreateParams.builder().model(model).store(false).maxCompletionTokens(2000)
                .addSystemMessage(system);
        for (JsonNode tool : tools) {
            var parameters = FunctionParameters.builder();
            Map<String, Object> schema = mapper.convertValue(tool.path("inputSchema"), new TypeReference<>() {});
            schema.forEach((key, value) -> parameters.putAdditionalProperty(key, JsonValue.from(value)));
            builder.addFunctionTool(FunctionDefinition.builder().name(tool.path("name").asText())
                    .description(tool.path("description").asText()).parameters(parameters.build()).build());
        }
        for (Message message : messages) {
            switch (message.role()) {
                case "user" -> builder.addUserMessage(message.content());
                case "assistant" -> {
                    var assistant = ChatCompletionAssistantMessageParam.builder();
                    if (message.content() != null) assistant.content(message.content());
                    for (Call call : message.calls()) assistant.addToolCall(ChatCompletionMessageFunctionToolCall.builder()
                            .id(call.id()).function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                    .name(call.name()).arguments(call.arguments()).build()).build());
                    builder.addMessage(assistant.build());
                }
                case "tool" -> builder.addMessage(ChatCompletionToolMessageParam.builder()
                        .toolCallId(message.toolCallId()).content(message.content()).build());
                default -> throw new IllegalArgumentException("Unsupported conversation role");
            }
        }
        long started = System.nanoTime();
        try {
            var completion = client.chat().completions().create(builder.build(),
                    RequestOptions.builder().timeout(Duration.ofSeconds(20)).build());
            completion.usage().ifPresent(usage -> AiCallTelemetry.success("ExpenseChat", completion.model(),
                    usage.promptTokens(), usage.promptTokensDetails().flatMap(details -> details.cachedTokens()).orElse(0L),
                    usage.completionTokens(), started));
            var message = completion.choices().getFirst().message();
            var calls = message.toolCalls().orElse(List.of()).stream().map(call -> {
                var function = call.asFunction();
                return new Call(function.id(), function.function().name(), function.function().arguments());
            }).toList();
            return new Reply(message.content().orElse(""), calls);
        } catch (RuntimeException ex) {
            AiCallTelemetry.failure("ExpenseChat", model, started);
            throw new WebApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_UNAVAILABLE",
                    "Money chat could not respond right now. Please try again.");
        }
    }
}
