package com.skillforge.marketing.service;

import com.skillforge.marketing.client.AnthropicClient;
import com.skillforge.marketing.domain.ProjectBrief;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PitchGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PitchGeneratorService.class);

    private final AnthropicClient anthropic;

    public PitchGeneratorService(AnthropicClient anthropic) {
        this.anthropic = anthropic;
    }

    /**
     * Gera Guild Pitch + Investor One-Pager a partir do project brief.
     * Retorna um Map com as chaves "guildPitch" e "investorOnePager".
     */
    public Map<String, String> generate(ProjectBrief brief) throws Exception {
        String prompt = buildPrompt(brief);
        log.info("Gerando pitches para projeto '{}'", brief.projectName());

        String raw = anthropic.generate("agency-system-prompt.txt", prompt, 0.8);
        return splitDocuments(raw);
    }

    private String buildPrompt(ProjectBrief brief) {
        return """
            Project Brief:
            Nome: %s
            Problema: %s
            Públicos: %s
            Diferencial: %s
            Tração: %s
            Modelo de receita: %s
            CTA: %s
            Contexto adicional: %s
            """.formatted(
                brief.projectName(),
                brief.problem(),
                String.join(", ", brief.audiences()),
                brief.differentiator(),
                brief.traction(),
                brief.revenueModel(),
                brief.cta(),
                brief.context() != null ? brief.context() : ""
            );
    }

    private Map<String, String> splitDocuments(String raw) {
        int guildIdx    = raw.indexOf("--- GUILD PITCH ---");
        int investorIdx = raw.indexOf("--- INVESTOR ONE-PAGER ---");

        String guildPitch = "";
        String investorOnePager = "";

        if (guildIdx >= 0 && investorIdx > guildIdx) {
            guildPitch       = raw.substring(guildIdx + "--- GUILD PITCH ---".length(), investorIdx).strip();
            investorOnePager = raw.substring(investorIdx + "--- INVESTOR ONE-PAGER ---".length()).strip();
        } else {
            // fallback: retorna tudo como guild pitch se o formato não foi respeitado
            guildPitch = raw;
            log.warn("Resposta do Claude não seguiu o formato esperado — retornando como guild pitch");
        }

        return Map.of("guildPitch", guildPitch, "investorOnePager", investorOnePager);
    }
}