package com.apps.deen_sa.schduler;

import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechOrchestrator;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LoadTestData {

    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;
    private final List<String> prompts = new ArrayList<>();

    public LoadTestData(SpeechOrchestrator orchestrator, ConversationContext conversationContext) {
        this.orchestrator = orchestrator;
        this.conversationContext = conversationContext;
    }

    // Core logic (reused)
    private void runTestPrompts() {
        for (String prompt : prompts) {
            orchestrator.process(prompt, conversationContext);
        }
    }

    // 1️⃣ Run immediately on startup
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runTestPrompts();
    }

    @PostConstruct
    private void load() {

        Yaml yaml = new Yaml();
        InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream("test-prompts.yml");

        if (is == null) {
            throw new IllegalStateException("test-prompts.yml not found in resources");
        }

        Map<String, Object> raw = yaml.load(is);

        Object list = raw.get("prompts");

        if (!(list instanceof List<?>)) {
            throw new IllegalStateException("Invalid format: 'prompts' must be a list");
        }

        for (Object o : (List<?>) list) {
            prompts.add(String.valueOf(o));
        }
    }
}
