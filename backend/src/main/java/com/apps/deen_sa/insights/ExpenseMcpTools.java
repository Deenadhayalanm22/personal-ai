package com.apps.deen_sa.insights;

import com.apps.deen_sa.entity.AppUserEntity;
import com.apps.deen_sa.exception.WebApiException;
import com.fasterxml.jackson.databind.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** FIN-EPIC-003: shared MCP tool catalog and execution used by the portal's embedded client. */
@Service
public class ExpenseMcpTools {
    private final ExpenseQueryTool query;
    private final ObjectMapper mapper;
    private final JsonNode definition;

    public ExpenseMcpTools(ExpenseQueryTool query, ObjectMapper mapper) throws IOException {
        this.query = query;
        this.mapper = mapper;
        try (var input = new ClassPathResource("insights/expense-query-tool.json").getInputStream()) {
            definition = mapper.readTree(input);
        }
    }
    public JsonNode definition() { return definition.deepCopy(); }
    public ExpenseQueryTool.Result call(AppUserEntity user, String name, String arguments) {
        if (!"query_expenses".equals(name)) throw ExpenseQueryTool.invalid("Unknown tool.");
        ExpenseQueryTool.Query request;
        try {
            if (arguments == null || arguments.length() > 6000) throw new IOException();
            request = mapper.readerFor(ExpenseQueryTool.Query.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(arguments);
        } catch (IOException | IllegalArgumentException ex) {
            throw ExpenseQueryTool.invalid("Invalid query arguments. Follow the query_expenses schema.");
        }
        return query.execute(user, request);
    }
    public Map<String, Object> callResult(AppUserEntity user, String name, String arguments) {
        try {
            var result = call(user, name, arguments);
            return Map.of("content", List.of(Map.of("type", "text", "text", json(result))),
                    "structuredContent", result, "isError", false);
        } catch (WebApiException ex) {
            return Map.of("content", List.of(Map.of("type", "text", "text", ex.getMessage())), "isError", true);
        }
    }
    public String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (IOException ex) { throw new IllegalStateException("Cannot serialize expense tool result", ex); }
    }
}
