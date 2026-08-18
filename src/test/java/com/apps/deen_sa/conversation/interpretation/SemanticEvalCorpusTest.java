package com.apps.deen_sa.conversation.interpretation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticEvalCorpusTest {
    @Test
    void corpusHasValidNamedExpectations() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/semantic-evals/conversation-turns.jsonl"), StandardCharsets.UTF_8))) {
            var cases = reader.lines().filter(line -> !line.isBlank()).map(line -> {
                try { return mapper.readTree(line); }
                catch (Exception exception) { throw new IllegalArgumentException(exception); }
            }).toList();
            assertThat(cases).hasSizeGreaterThanOrEqualTo(8);
            assertThat(cases).allSatisfy(testCase -> {
                assertThat(testCase.path("name").asText()).isNotBlank();
                assertThat(testCase.path("message").asText()).isNotBlank();
                assertThat(testCase.path("expectedTurnType").asText()).isNotBlank();
            });
        }
    }
}
