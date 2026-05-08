package com.skillforge.marketing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.marketing.client.AnthropicClient;
import com.skillforge.marketing.domain.PitchDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PitchValidatorService {

    private static final Logger log = LoggerFactory.getLogger(PitchValidatorService.class);

    private final AnthropicClient anthropic;
    private final ObjectMapper mapper;

    public PitchValidatorService(AnthropicClient anthropic, ObjectMapper mapper) {
        this.anthropic = anthropic;
        this.mapper = mapper;
    }

    /**
     * Valida cada documento com seu próprio validador:
     * - Guild Pitch: persona de dev cético
     * - Investor One-Pager: persona de angel investor
     * Confidence calculada pela média ponderada dos dois scores.
     */
    public PitchDraft validate(String guildPitch, String investorOnePager) throws Exception {
        log.info("Validando Guild Pitch com persona de dev cético");
        JsonNode guildResult = callValidator("guild-pitch-validator-prompt.txt", guildPitch);

        log.info("Validando Investor One-Pager com persona de angel investor");
        JsonNode investorResult = callValidator("investor-validator-prompt.txt", investorOnePager);

        return buildDraft(guildPitch, investorOnePager, guildResult, investorResult);
    }

    private JsonNode callValidator(String promptFile, String content) throws Exception {
        String raw = anthropic.generate(promptFile, content, 0.1);
        String json = raw.contains("{") ? raw.substring(raw.indexOf("{"), raw.lastIndexOf("}") + 1) : raw;
        return mapper.readTree(json);
    }

    private PitchDraft buildDraft(String guildPitch, String investorOnePager,
                                   JsonNode guildNode, JsonNode investorNode) {
        // Scores do Guild Pitch (perspectiva dev)
        double gClareza       = guildNode.path("scores").path("clareza").asDouble(5.0);
        double gDor           = guildNode.path("scores").path("dor").asDouble(5.0);
        double gDiferencial   = guildNode.path("scores").path("diferencial").asDouble(5.0);
        double gCredibilidade = guildNode.path("scores").path("credibilidade").asDouble(5.0);

        // Scores do Investor One-Pager (perspectiva investidor)
        double iClareza       = investorNode.path("scores").path("clareza").asDouble(5.0);
        double iDor           = investorNode.path("scores").path("dor").asDouble(5.0);
        double iDiferencial   = investorNode.path("scores").path("diferencial").asDouble(5.0);
        double iCredibilidade = investorNode.path("scores").path("credibilidade").asDouble(5.0);

        // Média entre os dois documentos
        double clareza       = (gClareza + iClareza) / 2.0;
        double dor           = (gDor + iDor) / 2.0;
        double diferencial   = (gDiferencial + iDiferencial) / 2.0;
        double credibilidade = (gCredibilidade + iCredibilidade) / 2.0;

        boolean defensavel = guildNode.path("diferencial_defensavel").asBoolean(false)
                          && investorNode.path("diferencial_defensavel").asBoolean(false);
        boolean ctaOk      = guildNode.path("cta_presente").asBoolean(false)
                          && investorNode.path("cta_presente").asBoolean(false);

        String veredictoGuild    = guildNode.path("veredicto").asText("");
        String veredictoInvestor = investorNode.path("veredicto").asText("");
        String veredicto = "Dev: " + veredictoGuild + " | Investidor: " + veredictoInvestor;

        double avg = (clareza + dor + diferencial + credibilidade) / 4.0;
        double confidence = computeConfidence(avg, defensavel, ctaOk);
        boolean valid = avg >= 6.0 && defensavel && ctaOk;

        log.info("Validação — avg: {} (guild avg: {} | investor avg: {}), confidence: {}, valid: {}",
            "%.1f".formatted(avg),
            "%.1f".formatted((gClareza + gDor + gDiferencial + gCredibilidade) / 4.0),
            "%.1f".formatted((iClareza + iDor + iDiferencial + iCredibilidade) / 4.0),
            "%.2f".formatted(confidence), valid);

        return new PitchDraft(guildPitch, investorOnePager,
            clareza, dor, diferencial, credibilidade,
            defensavel, ctaOk, veredicto, confidence, valid);
    }

    private double computeConfidence(double avg, boolean defensavel, boolean ctaPresente) {
        if (avg < 6.0) return 0.0;

        double confidence = avg >= 8.5 ? 0.92 : 0.85;
        if (avg < 7.0)       confidence *= 0.80;
        if (!defensavel)     confidence *= 0.70;
        if (!ctaPresente)    confidence *= 0.80;

        return Math.round(confidence * 100.0) / 100.0;
    }
}