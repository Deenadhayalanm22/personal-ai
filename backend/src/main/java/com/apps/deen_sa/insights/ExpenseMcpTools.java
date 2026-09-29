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
    private final List<JsonNode> definitions;
    private final FinancialRecordsTool records;
    private final MonthlyPlanningTool planning;

    public ExpenseMcpTools(ExpenseQueryTool query, FinancialRecordsTool records, MonthlyPlanningTool planning, ObjectMapper mapper) throws IOException {
        this.query = query; this.records = records; this.planning = planning; this.mapper = mapper;
        var catalog = new java.util.ArrayList<JsonNode>();
        for (String name : List.of("expense-query", "financial-records", "monthly-plan", "monthly-scenario")) {
            try (var input = new ClassPathResource("insights/" + name + "-tool.json").getInputStream()) {
                catalog.add(mapper.readTree(input));
            }
        }
        definitions = List.copyOf(catalog);
    }
    public List<JsonNode> definitions() { return definitions.stream().<JsonNode>map(JsonNode::deepCopy).toList(); }
    public Object call(AppUserEntity user, String name, String arguments) {
        return switch (name) {
            case "query_expenses" -> query.execute(user, parse(arguments, ExpenseQueryTool.Query.class));
            case "read_financial_records" -> records.read(user, parse(arguments, FinancialRecordsTool.Request.class));
            case "read_monthly_plan" -> planning.read(user, parse(arguments, MonthlyPlanningTool.MonthRequest.class));
            case "simulate_monthly_plan" -> planning.simulate(user, parse(arguments, MonthlyPlanningTool.ScenarioRequest.class));
            default -> throw ExpenseQueryTool.invalid("Unknown tool.");
        };
    }
    private <T> T parse(String arguments, Class<T> type) {
        try {
            if (arguments == null || arguments.length() > 6000) throw new IOException();
            return mapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(arguments);
        } catch (IOException | IllegalArgumentException ex) {
            throw ExpenseQueryTool.invalid("Invalid tool arguments. Follow the selected tool's schema.");
        }
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
