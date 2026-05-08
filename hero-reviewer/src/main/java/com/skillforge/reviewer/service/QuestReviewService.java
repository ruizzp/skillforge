package com.skillforge.reviewer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.reviewer.amqp.ReviewMessage;
import com.skillforge.reviewer.amqp.ReviewResultMessage;
import com.skillforge.reviewer.client.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class QuestReviewService {

    private static final Logger log = LoggerFactory.getLogger(QuestReviewService.class);

    private final OllamaClient ollamaClient;
    private final ObjectMapper mapper;
    private final String systemPrompt;

    @Value("${skillforge.reviewer.approval-threshold:0.70}")
    private double approvalThreshold;

    public QuestReviewService(OllamaClient ollamaClient) throws IOException {
        this.ollamaClient = ollamaClient;
        this.mapper       = new ObjectMapper();
        this.systemPrompt = loadSystemPrompt();
    }

    /**
     * Reviews a submitted solution against the quest's Definition of Done.
     * Returns a ReviewResultMessage with the verdict.
     */
    public ReviewResultMessage review(ReviewMessage msg) {
        String doD = extractDoD(msg.questBody());

        if (doD.isBlank()) {
            log.info("Quest {} não possui DoD — aprovação automática com ressalva.", msg.questId());
            return new ReviewResultMessage(
                    msg.questId(),
                    msg.heroId(),
                    true,
                    "",
                    0.5,
                    msg.revisionCount(),
                    ollamaClient.getModel() + " (auto-approved: no DoD)"
            );
        }

        String userPrompt = buildUserPrompt(msg.questTitle(), doD, msg.solution());

        String rawResponse = ollamaClient.chat(systemPrompt, userPrompt);

        if (rawResponse == null) {
            log.warn("Ollama indisponível para revisão da quest {} — aprovação automática para não bloquear fluxo.",
                    msg.questId());
            return new ReviewResultMessage(
                    msg.questId(),
                    msg.heroId(),
                    true,
                    "",
                    0.5,
                    msg.revisionCount(),
                    "auto-approved (ollama unavailable)"
            );
        }

        return parseReviewResponse(msg, rawResponse);
    }

    /**
     * Extracts the Definition of Done section from the quest body.
     * Looks for "## Definition of Done", "## DoD", or "## Critérios de Aceite".
     */
    String extractDoD(String body) {
        if (body == null || body.isBlank()) return "";

        String[] dodHeaders = {
            "## Definition of Done",
            "## DoD",
            "## Critérios de Aceite"
        };

        for (String header : dodHeaders) {
            int start = body.indexOf(header);
            if (start < 0) continue;

            int contentStart = start + header.length();
            // Find the next ## section after the DoD header
            int nextSection = body.indexOf("\n##", contentStart);
            String section = nextSection > 0
                    ? body.substring(contentStart, nextSection)
                    : body.substring(contentStart);

            String trimmed = section.trim();
            if (!trimmed.isBlank()) return trimmed;
        }

        return "";
    }

    private String buildUserPrompt(String questTitle, String doD, String solution) {
        return """
                Quest: %s

                ## Definition of Done
                %s

                ## Solução Submetida
                %s

                Avalie se a solução atende ao DoD e responda com JSON válido conforme o formato especificado.
                """.formatted(questTitle, doD, solution);
    }

    private ReviewResultMessage parseReviewResponse(ReviewMessage msg, String rawResponse) {
        try {
            // Extract JSON from response (LLM may wrap it in markdown code blocks)
            String jsonStr = extractJson(rawResponse);
            JsonNode node = mapper.readTree(jsonStr);

            boolean approved  = node.path("approved").asBoolean(false);
            double score      = node.path("score").asDouble(0.0);
            String feedback   = node.path("feedback").asText("");
            String summary    = node.path("summary").asText("");

            // Apply approval threshold: even if LLM says approved, require minimum score
            if (approved && score < approvalThreshold) {
                log.info("Quest {} score {:.2f} abaixo do threshold {:.2f} — rejeitando",
                        msg.questId(), score, approvalThreshold);
                approved = false;
                if (feedback.isBlank()) {
                    feedback = "Score de qualidade (%s) abaixo do mínimo exigido (%.0f%%). %s"
                            .formatted(String.format("%.0f%%", score * 100),
                                    approvalThreshold * 100, summary);
                }
            }

            log.info("Revisão concluída — quest: {} | aprovado: {} | score: {} | summary: {}",
                    msg.questId(), approved, score, summary);

            return new ReviewResultMessage(
                    msg.questId(),
                    msg.heroId(),
                    approved,
                    feedback,
                    score,
                    msg.revisionCount(),
                    ollamaClient.getModel()
            );

        } catch (Exception e) {
            log.warn("Falha ao parsear resposta do revisor para quest {} — aprovação automática. Erro: {}",
                    msg.questId(), e.getMessage());
            log.debug("Resposta bruta do LLM: {}", rawResponse);
            return new ReviewResultMessage(
                    msg.questId(),
                    msg.heroId(),
                    true,
                    "",
                    0.5,
                    msg.revisionCount(),
                    ollamaClient.getModel() + " (auto-approved: parse error)"
            );
        }
    }

    /**
     * Extracts a JSON object from a string that may contain markdown code fences.
     */
    private static String extractJson(String text) {
        if (text == null) return "{}";
        // Try to find ```json ... ``` block
        int codeStart = text.indexOf("```json");
        if (codeStart >= 0) {
            int contentStart = text.indexOf('\n', codeStart) + 1;
            int codeEnd = text.indexOf("```", contentStart);
            if (codeEnd > 0) return text.substring(contentStart, codeEnd).trim();
        }
        // Try plain ``` block
        codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int contentStart = text.indexOf('\n', codeStart) + 1;
            int codeEnd = text.indexOf("```", contentStart);
            if (codeEnd > 0) return text.substring(contentStart, codeEnd).trim();
        }
        // Fall back to raw JSON extraction
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start >= 0 && end >= 0) return text.substring(start, end + 1);
        return text.trim();
    }

    private static String loadSystemPrompt() throws IOException {
        var resource = new ClassPathResource("prompts/review-system-prompt.txt");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}