package com.apps.deen_sa.conversation.interpretation;

import com.apps.deen_sa.extension.api.EventCapability;
import com.apps.deen_sa.extension.runtime.ExtensionCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiConversationInterpreterSchemaTest {

    @Test
    @SuppressWarnings("unchecked")
    void taxonomyCandidateUsesStrictNestedObjectSchema() throws Exception {
        EventCapability capability = mock(EventCapability.class);
        when(capability.eventType()).thenReturn("EXPENSE");
        when(capability.fieldTypes()).thenReturn(Map.of(
                "amount", "number",
                "taxonomyCandidate", "object"));
        OpenAiConversationInterpreter interpreter = new OpenAiConversationInterpreter(
                mock(OpenAIClient.class), new ObjectMapper(), mock(ExtensionCatalog.class),
                "test-model", "test-model", 0.55);

        Method method = OpenAiConversationInterpreter.class.getDeclaredMethod("responseSchema", Collection.class);
        method.setAccessible(true);
        Map<String, Object> root = (Map<String, Object>) method.invoke(interpreter, List.of(capability));

        Map<String, Object> rootProperties = (Map<String, Object>) root.get("properties");
        Map<String, Object> events = (Map<String, Object>) rootProperties.get("events");
        Map<String, Object> event = (Map<String, Object>) events.get("items");
        Map<String, Object> eventProperties = (Map<String, Object>) event.get("properties");
        Map<String, Object> fields = (Map<String, Object>) eventProperties.get("fields");
        Map<String, Object> fieldProperties = (Map<String, Object>) fields.get("properties");
        Map<String, Object> candidateNullable = (Map<String, Object>) fieldProperties.get("taxonomyCandidate");
        List<Map<String, Object>> variants = (List<Map<String, Object>>) candidateNullable.get("anyOf");
        Map<String, Object> candidate = variants.getFirst();

        assertEquals(false, candidate.get("additionalProperties"));
        assertEquals(List.of("category", "subcategory", "itemConcept", "confidence"), candidate.get("required"));
        assertTrue(((Map<String, Object>) candidate.get("properties")).containsKey("confidence"));
        assertEquals("null", variants.get(1).get("type"));
        assertFalse(candidate.isEmpty());
    }
}
