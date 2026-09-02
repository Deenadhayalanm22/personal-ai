package com.apps.deen_sa.v2.controller;

import com.apps.deen_sa.v2.orchestration.WhatsAppIngestionOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WhatsAppWebhookV2ControllerTest {
    private WhatsAppIngestionOrchestrator orchestrator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orchestrator = mock(WhatsAppIngestionOrchestrator.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WhatsAppWebhookV2Controller(orchestrator))
                .build();
    }

    @Test
    void delegatesTheEntirePayloadToTheOrchestrator() throws Exception {
        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entry": [{"changes": [{"value": {"messages": [{
                                  "id": "wamid.1",
                                  "from": "919876543210",
                                  "type": "text",
                                  "text": {"body": "Paid ₹250 for lunch"}
                                }]}}]}]}
                                """))
                .andExpect(status().isOk());

        verify(orchestrator).ingest(any());
        verifyNoMoreInteractions(orchestrator);
    }
}
