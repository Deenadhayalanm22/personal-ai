package com.apps.deen_sa.llm;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class PromptLoader {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String path) {
        return cache.computeIfAbsent(path, this::readFromClasspath);
    }

    public String combine(String... paths) {
        return Arrays.stream(paths)
                .map(this::load)
                .collect(Collectors.joining("\n\n"));
    }

    private String readFromClasspath(String path) {
        try (InputStream is = getClass()
                .getClassLoader()
                .getResourceAsStream(path)) {

            if (is == null) {
                throw new IllegalArgumentException(
                        "Prompt file not found on classpath: " + path
                );
            }

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to load prompt file: " + path, e
            );
        }
    }
}
