package com.skillforge.reviewer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String model;

    public OllamaClient(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3.2}") String model) {
        this.baseUrl = baseUrl;
        this.model   = model;
        this.mapper  = new ObjectMapper();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Sends a chat request to Ollama and returns the assistant response content.
     * Returns null if Ollama is unavailable or an error occurs.
     */
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String url = baseUrl + "/api/chat";

            var messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user",   "content", userPrompt)
            );

            String requestBody = mapper.writeValueAsString(Map.of(
                    "model",    model,
                    "messages", messages,
                    "stream",   false
            ));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Ollama retornou status {} — body: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode node = mapper.readTree(response.body());
            String content = node.path("message").path("content").asText("");

            if (content.isBlank()) {
                log.warn("Ollama retornou conteúdo vazio para modelo '{}'", model);
                return null;
            }

            return content;

        } catch (java.net.ConnectException e) {
            log.warn("Ollama indisponível em {} — {}", baseUrl, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Erro ao chamar Ollama: {}", e.getMessage());
            return null;
        }
    }

    public String getModel() {
        return model;
    }
}