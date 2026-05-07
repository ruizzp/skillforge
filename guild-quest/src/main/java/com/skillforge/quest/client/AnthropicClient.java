package com.skillforge.quest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL   = "claude-sonnet-4-6";

    @Value("${anthropic.api-key:}")
    private String apiKey;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private String systemPrompt;

    public AnthropicClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String generate(String userMessage) throws Exception {
        if (apiKey.isBlank()) throw new IllegalStateException("ANTHROPIC_API_KEY não configurado");

        var body = mapper.writeValueAsString(Map.of(
            "model", MODEL,
            "max_tokens", 4096,
            "system", loadSystemPrompt(),
            "messages", List.of(Map.of("role", "user", "content", userMessage))
        ));

        var request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Anthropic API error %d: %s".formatted(response.statusCode(), response.body()));
        }

        return mapper.readTree(response.body())
            .path("content").get(0).path("text").asText();
    }

    private String loadSystemPrompt() throws Exception {
        if (systemPrompt == null) {
            var resource = new ClassPathResource("prompts/guardian-system-prompt.txt");
            systemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        return systemPrompt;
    }
}