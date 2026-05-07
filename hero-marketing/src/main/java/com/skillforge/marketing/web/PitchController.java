package com.skillforge.marketing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.marketing.domain.PitchDraft;
import com.skillforge.marketing.domain.ProjectBrief;
import com.skillforge.marketing.service.PitchGeneratorService;
import com.skillforge.marketing.service.PitchValidatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class PitchController {

    private static final Logger log = LoggerFactory.getLogger(PitchController.class);

    private final PitchGeneratorService generator;
    private final PitchValidatorService validator;
    private final ObjectMapper mapper;

    public PitchController(PitchGeneratorService generator,
                           PitchValidatorService validator,
                           ObjectMapper mapper) {
        this.generator = generator;
        this.validator = validator;
        this.mapper    = mapper;
    }

    @GetMapping("/health")
    public Map<String, String> health() throws Exception {
        var resource = new ClassPathResource("hero-marketing-manifest.json");
        String heroId = mapper.readTree(
            new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
        ).path("heroId").asText("hero-marketing");
        return Map.of("heroId", heroId, "status", "UP");
    }

    @PostMapping("/api/pitch")
    public ResponseEntity<?> createPitch(@RequestBody ProjectBrief brief) {
        log.info("Recebido request de pitch para projeto '{}'", brief.projectName());
        try {
            Map<String, String> docs = generator.generate(brief);
            PitchDraft draft = validator.validate(docs.get("guildPitch"), docs.get("investorOnePager"));

            if (!draft.valid()) {
                return ResponseEntity.unprocessableEntity().body(Map.of(
                    "valid",     false,
                    "confidence", draft.confidence(),
                    "veredicto", draft.veredicto(),
                    "scores", Map.of(
                        "clareza",       draft.clareza(),
                        "dor",           draft.dor(),
                        "diferencial",   draft.diferencial(),
                        "credibilidade", draft.credibilidade()
                    )
                ));
            }

            return ResponseEntity.ok(Map.of(
                "valid",                  true,
                "confidence",             draft.confidence(),
                "diferencialDefensavel",  draft.diferencialDefensavel(),
                "ctaPresente",            draft.ctaPresente(),
                "veredicto",              draft.veredicto(),
                "scores", Map.of(
                    "clareza",       draft.clareza(),
                    "dor",           draft.dor(),
                    "diferencial",   draft.diferencial(),
                    "credibilidade", draft.credibilidade()
                ),
                "guildPitch",        draft.guildPitch(),
                "investorOnePager",  draft.investorOnePager()
            ));

        } catch (Exception e) {
            log.error("Falha ao processar pitch: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}