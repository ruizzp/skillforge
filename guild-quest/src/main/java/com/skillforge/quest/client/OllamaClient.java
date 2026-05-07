package com.skillforge.quest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Component
public class OllamaClient {

    @Value("${hero.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${skillforge.quest.validator-model:meditron}")
    private String validatorModel;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    public OllamaClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Envia um prompt de validação ao SLM de domínio.
     * Retorna o texto da resposta ou lança exceção se Ollama indisponível.
     */
    public String validate(String prompt) throws Exception {
        var body = mapper.writeValueAsString(Map.of(
            "model", validatorModel,
            "prompt", prompt,
            "stream", false
        ));

        var request = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl + "/api/generate"))
            .header("content-type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama error %d".formatted(response.statusCode()));
        }

        return mapper.readTree(response.body()).path("response").asText();
    }

    public boolean isAvailable() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/tags"))
                .GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}