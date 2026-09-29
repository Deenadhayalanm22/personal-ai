package com.apps.deen_sa.controller;

import com.apps.deen_sa.insights.*;
import com.apps.deen_sa.service.WebAuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** FIN-EPIC-003 and FIN-EPIC-004: authenticated V1 expense chat and private MCP transport. */
@RestController
@RequestMapping("/api/web/expense-chat")
public class ExpenseChatController {
    private final WebAuthenticationService authentication;
    private final ExpenseChatService chat;
    private final ExpenseMcpTools tools;
    private final Set<String> origins;

    public ExpenseChatController(WebAuthenticationService authentication, ExpenseChatService chat, ExpenseMcpTools tools,
                                 @Value("${app.cors.allowed-origins:}") String allowedOrigins,
                                 @Value("${app.web.base-url:}") String webBaseUrl) {
        this.authentication = authentication; this.chat = chat; this.tools = tools;
        origins = new HashSet<>();
        for (String value : (allowedOrigins + "," + webBaseUrl).split(",")) {
            if (!value.isBlank()) {
                try {
                    var uri = java.net.URI.create(value.trim());
                    origins.add(uri.getScheme() + "://" + uri.getRawAuthority());
                } catch (IllegalArgumentException ignored) { /* Invalid origins grant no access. */ }
            }
        }
    }

    @PostMapping
    public ResponseEntity<ExpenseChatService.Response> chat(@CookieValue(name = "WEB_SESSION", required = false) String token,
                                            @RequestBody ExpenseChatService.Request request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.chat(authentication.authenticate(token), request));
    }

    // Stateless Streamable HTTP JSON response mode. No SSE or server-originated requests.
    // This private endpoint uses the portal cookie; it is not a public third-party OAuth integration.
    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> mcp(@CookieValue(name = "WEB_SESSION", required = false) String token,
                                  @RequestBody JsonNode request, HttpServletRequest http) {
        var user = authentication.authenticate(token);
        String origin = http.getHeader("Origin");
        if (origin != null && !origins.contains(origin)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String version = http.getHeader("MCP-Protocol-Version");
        if (version != null && !"2025-06-18".equals(version)) return ResponseEntity.badRequest().build();
        JsonNode id = request.get("id");
        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual() || (id != null && !id.isTextual() && !id.isNumber()))
            return rpcError(null, -32600, "Invalid request");
        String method = request.path("method").asText();
        if (id == null) return ResponseEntity.accepted().build();
        Object result;
        switch (method) {
            case "initialize" -> result = Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "personal-expense", "version", "1.0.0"));
            case "ping" -> result = Map.of();
            case "tools/list" -> result = Map.of("tools", List.of(tools.definition()));
            case "tools/call" -> {
                JsonNode params = request.path("params");
                if (!params.path("name").isTextual() || !params.path("arguments").isObject())
                    return rpcError(id, -32602, "Expected tool name and arguments");
                try { result = tools.callResult(user, params.path("name").asText(), params.path("arguments").toString()); }
                catch (org.springframework.dao.DataAccessException ex) { return rpcError(id, -32603, "Expense data is temporarily unavailable"); }
            }
            default -> { return rpcError(id, -32601, "Method not found"); }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("jsonrpc", "2.0", "id", id, "result", result));
    }
    private ResponseEntity<?> rpcError(JsonNode id, int code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0"); body.put("id", id);
        body.put("error", Map.of("code", code, "message", message));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
