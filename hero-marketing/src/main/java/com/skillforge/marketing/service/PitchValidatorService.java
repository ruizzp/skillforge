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
     * Valida os dois documentos com persona de angel investor (temperatura baixa = cético).
     * Confiança calculada a partir dos scores + flags estruturais.
     */
    public PitchDraft validate(String guildPitch, String investorOnePager) throws Exception {
        String combined = "GUILD PITCH:\n" + guildPitch + "\n\nINVESTOR ONE-PAGER:\n" + investorOnePager;
        log.info("Validando pitches com persona de angel investor");

        String raw = anthropic.generate("investor-validator-prompt.txt", combined, 0.1);
        return parseValidation(raw, guildPitch, investorOnePager);
    }

    private PitchDraft parseValidation(String raw, String guildPitch, String investorOnePager) {
        try {
            // Claude pode retornar o JSON dentro de um bloco markdown
            String json = raw.contains("{") ? raw.substring(raw.indexOf("{"), raw.lastIndexOf("}") + 1) : raw;
            JsonNode node = mapper.readTree(json);

            double clareza        = node.path("scores").path("clareza").asDouble(5.0);
            double dor            = node.path("scores").path("dor").asDouble(5.0);
            double diferencial    = node.path("scores").path("diferencial").asDouble(5.0);
            double credibilidade  = node.path("scores").path("credibilidade").asDouble(5.0);
            boolean defens        = node.path("diferencial_defensavel").asBoolean(false);
            boolean ctaOk         = node.path("cta_presente").asBoolean(false);
            String veredicto      = node.path("veredicto").asText("");

            double avg = (clareza + dor + diferencial + credibilidade) / 4.0;
            double confidence = computeConfidence(avg, defens, ctaOk);
            boolean valid = avg >= 6.0 && defens && ctaOk;

            log.info("Validação concluída — avg score: {:.1f}, confidence: {:.2f}, valid: {}", avg, confidence, valid);
            return new PitchDraft(guildPitch, investorOnePager,
                clareza, dor, diferencial, credibilidade,
                defens, ctaOk, veredicto, confidence, valid);

        } catch (Exception e) {
            log.error("Falha ao parsear resposta do validador: {}", e.getMessage());
            return new PitchDraft(guildPitch, investorOnePager,
                0, 0, 0, 0, false, false,
                "Falha na validação: " + e.getMessage(), 0.0, false);
        }
    }

    private double computeConfidence(double avg, boolean diferencialDefensavel, boolean ctaPresente) {
        double confidence = 0.85;

        if (avg < 6.0)       return 0.0;
        if (avg < 7.0)       confidence *= 0.80;
        else if (avg > 8.5)  confidence = 0.92;

        if (!diferencialDefensavel) confidence *= 0.70;
        if (!ctaPresente)           confidence *= 0.80;

        return Math.round(confidence * 100.0) / 100.0;
    }
}