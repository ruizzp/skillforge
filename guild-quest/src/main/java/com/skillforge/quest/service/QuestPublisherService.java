package com.skillforge.quest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.quest.domain.QuestDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publica a quest aprovada como GitHub Issue com as labels corretas.
 * O hub faz fetchQuests() a cada 5 min — a quest aparece automaticamente.
 */
@Service
public class QuestPublisherService {

    private static final Logger log = LoggerFactory.getLogger(QuestPublisherService.class);
    private static final String API = "https://api.github.com";

    @Value("${skillforge.github.token:}")
    private String token;

    @Value("${skillforge.github.owner:}")
    private String owner;

    @Value("${skillforge.github.repo:}")
    private String repo;

    @Value("${skillforge.quest.confidence-threshold:0.75}")
    private double threshold;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper;

    public QuestPublisherService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Publica no GitHub se confidence >= threshold.
     * Retorna a URL da issue criada, ou vazio se não publicado.
     */
    public String publish(QuestDraft draft) {
        if (draft.confidence() < threshold) {
            log.warn("Quest não publicada — confiança {} abaixo do threshold {}", draft.confidence(), threshold);
            return "";
        }

        if (token.isBlank() || owner.isBlank() || repo.isBlank()) {
            log.warn("GitHub não configurado — quest não publicada. Configure skillforge.github.*");
            return "";
        }

        try {
            String url = "%s/repos/%s/%s/issues".formatted(API, owner, repo);
            String labels = buildLabels(draft);
            String body = """
                {"title":"[%s] %s","body":%s,"labels":%s}
                """.formatted(
                    nextQuestId(),
                    draft.title(),
                    jsonEscape(draft.markdown()),
                    labels
                ).strip();

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                String issueUrl = mapper.readTree(response.body()).path("html_url").asText("");
                log.info("Quest publicada: {}", issueUrl);
                return issueUrl;
            }
            log.error("GitHub retornou {}: {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Falha ao publicar quest no GitHub: {}", e.getMessage());
        }
        return "";
    }

    private String buildLabels(QuestDraft draft) {
        var sb = new StringBuilder("[\"quest\",\"")
            .append(draft.rarity().toLowerCase()).append("\",\"xp:")
            .append(draft.xpReward()).append("\"");
        for (String skill : draft.requiredSkills()) {
            sb.append(",\"skill:").append(skill).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Consulta todas as issues com label "quest" e retorna o próximo ID sequencial.
     * Lê títulos no formato [QUEST-NNN] e incrementa o maior encontrado.
     * Fallback para QUEST-001 se nenhuma quest existir ainda.
     */
    private String nextQuestId() throws Exception {
        String url = "%s/repos/%s/%s/issues?labels=quest&state=all&per_page=100"
            .formatted(API, owner, repo);

        var request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .GET().build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("Não foi possível consultar quests existentes ({}), usando QUEST-001", response.statusCode());
            return "QUEST-001";
        }

        Pattern pattern = Pattern.compile("\\[QUEST-(\\d+)\\]");
        int max = 0;

        for (JsonNode issue : mapper.readTree(response.body())) {
            String title = issue.path("title").asText("");
            Matcher matcher = pattern.matcher(title);
            if (matcher.find()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }

        return "QUEST-%03d".formatted(max + 1);
    }

    private String jsonEscape(String text) {
        return "\"" + text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            + "\"";
    }
}