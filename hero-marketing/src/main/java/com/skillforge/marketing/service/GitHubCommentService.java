package com.skillforge.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.marketing.domain.PitchDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitHubCommentService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCommentService.class);
    private static final String API = "https://api.github.com";

    @Value("${skillforge.github.token:}")
    private String token;

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();

    public GitHubCommentService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Posta os artefatos gerados como comentário na issue da quest.
     * questUrl exemplo: https://github.com/owner/repo/issues/7
     */
    public void postArtifacts(String questUrl, String questId, PitchDraft draft) {
        if (token.isBlank() || questUrl == null || questUrl.isBlank()) {
            log.warn("GitHub não configurado ou questUrl vazio — artefatos não postados");
            return;
        }

        String commentUrl = buildCommentUrl(questUrl);
        if (commentUrl == null) {
            log.warn("Não foi possível extrair owner/repo/issue de: {}", questUrl);
            return;
        }

        String body = buildCommentBody(questId, draft);

        try {
            String payload = mapper.writeValueAsString(Map.of("body", body));
            var request = HttpRequest.newBuilder()
                .uri(URI.create(commentUrl))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                log.info("Artefatos postados na issue: {}", questUrl);
            } else {
                log.error("GitHub retornou {} ao postar comentário: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Falha ao postar artefatos no GitHub: {}", e.getMessage());
        }
    }

    private String buildCommentUrl(String questUrl) {
        // https://github.com/owner/repo/issues/123 → https://api.github.com/repos/owner/repo/issues/123/comments
        Matcher m = Pattern.compile("github\\.com/([^/]+)/([^/]+)/issues/(\\d+)").matcher(questUrl);
        if (!m.find()) return null;
        return "%s/repos/%s/%s/issues/%s/comments".formatted(API, m.group(1), m.group(2), m.group(3));
    }

    private String buildCommentBody(String questId, PitchDraft draft) {
        String status = draft.valid() ? "✅ Aprovado" : "⚠️ Necessita revisão";
        String avg = String.format("%.1f", (draft.clareza() + draft.dor() + draft.diferencial() + draft.credibilidade()) / 4.0);

        return """
            ## 🎯 Solução gerada por `hero-marketing` — %s

            **Confidence:** %.0f%% | **Score médio:** %s/10 | **Diferencial defensável:** %s | **CTA presente:** %s

            > %s

            ---

            ### Guild Pitch (para devs)

            %s

            ---

            ### Investor One-Pager (para investidores)

            %s

            ---

            *Gerado automaticamente pela skill `pitch-scribe`. Scores: clareza %.0f · dor %.0f · diferencial %.0f · credibilidade %.0f*
            """.formatted(
                status,
                draft.confidence() * 100,
                avg,
                draft.diferencialDefensavel() ? "sim" : "não",
                draft.ctaPresente() ? "sim" : "não",
                draft.veredicto(),
                draft.guildPitch(),
                draft.investorOnePager(),
                draft.clareza(), draft.dor(), draft.diferencial(), draft.credibilidade()
            );
    }
}