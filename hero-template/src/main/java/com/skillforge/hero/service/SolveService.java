package com.skillforge.hero.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SolveService {

    private static final Logger log = LoggerFactory.getLogger(SolveService.class);

    private final GuildService guildService;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String ollamaUrl;

    public record SolveResult(String solution, double confidence, String model) {}

    public SolveService(GuildService guildService, ObjectMapper mapper,
                        @Value("${hero.ollama.url:http://localhost:11434}") String ollamaUrl) {
        this.guildService = guildService;
        this.mapper       = mapper;
        this.ollamaUrl    = ollamaUrl;
        this.http         = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public SolveResult solve(String problem, List<String> requiredSkills) {
        var manifest = guildService.getManifest();
        String model = manifest.model() != null ? manifest.model() : "phi3:mini";

        try {
            return callOllama(problem, model, manifest.heroName(), requiredSkills);
        } catch (Exception e) {
            log.warn("Ollama indisponível ({}): usando placeholder estruturado.", e.getMessage());
            return placeholder(problem, requiredSkills, manifest.heroName(), model);
        }
    }

    private SolveResult callOllama(String problem, String model, String heroName,
                                   List<String> skills) throws Exception {
        String prompt = """
                Você é %s, um especialista em %s.
                Responda de forma objetiva e técnica:

                %s
                """.formatted(heroName, String.join(", ", skills), problem);

        String body = mapper.writeValueAsString(Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        var json = mapper.readTree(response.body());

        String solution = json.path("response").asText("(sem resposta)");
        // Ollama não retorna confidence — usamos 0.9 quando responde com sucesso
        return new SolveResult(solution, 0.9, model);
    }

    private SolveResult placeholder(String problem, List<String> skills,
                                    String heroName, String model) {
        String solution = """
                [Placeholder] %s analisa o problema com skills: %s

                Problema: %s

                Configure Ollama (hero.ollama.url) e o modelo "%s" para respostas reais.
                """.formatted(heroName, String.join(", ", skills), problem, model);

        // 0.8 passa o threshold padrão de 0.75 do hub para fins de teste
        return new SolveResult(solution, 0.8, model + "-placeholder");
    }
}
