package com.skillforge.quest.service;

import com.skillforge.quest.domain.QuestDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
                // extrai html_url da resposta
                String issueUrl = response.body().replaceAll(".*\"html_url\":\"([^\"]+)\".*", "$1");
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

    private String nextQuestId() {
        // TODO: consultar GitHub para saber o próximo número disponível
        return "QUEST";
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