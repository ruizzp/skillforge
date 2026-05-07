package com.skillforge.quest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.quest.client.AnthropicClient;
import com.skillforge.quest.client.OllamaClient;
import com.skillforge.quest.domain.QuestDraft;
import com.skillforge.quest.domain.QuestRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QuestGuardianService {

    private static final Logger log = LoggerFactory.getLogger(QuestGuardianService.class);

    private final ProfileValidatorService profileValidator;
    private final AnthropicClient anthropic;
    private final OllamaClient ollama;
    private final ObjectMapper mapper;

    @Value("${skillforge.quest.validator-model:meditron}")
    private String validatorModel;

    public QuestGuardianService(ProfileValidatorService profileValidator,
                                AnthropicClient anthropic,
                                OllamaClient ollama,
                                ObjectMapper mapper) {
        this.profileValidator = profileValidator;
        this.anthropic = anthropic;
        this.ollama = ollama;
        this.mapper = mapper;
    }

    public QuestDraft createQuest(QuestRequest request) {
        // 1. Guardrail: valida o domain profile antes de gerar qualquer coisa
        var validation = profileValidator.validate(request.domain());
        if (!validation.valid()) {
            log.warn("Domain profile bloqueado para '{}': {}", request.domain(), validation.missing());
            String report = "Domain profile incompleto:\n- " + String.join("\n- ", validation.missing());
            return new QuestDraft(report, "", request.level(), 0, List.of(), 0.0, "blocked");
        }

        // 2. Claude API gera o rascunho estruturado
        String questMarkdown;
        try {
            String prompt = buildGuardianPrompt(request);
            questMarkdown = anthropic.generate(prompt);
            log.info("Rascunho gerado pelo Claude para domínio '{}'", request.domain());
        } catch (Exception e) {
            log.error("Claude API falhou: {}", e.getMessage());
            return new QuestDraft("Falha ao chamar Claude API: " + e.getMessage(),
                "", request.level(), 0, List.of(), 0.0, "error");
        }

        double confidence = 0.85;

        // 3. Meditron valida os fixtures gerados (se disponível)
        if (ollama.isAvailable()) {
            confidence = validateWithDomainSLM(questMarkdown, request.domain(), confidence);
        } else {
            log.warn("Meditron indisponível — pulando validação de domínio. Confiança reduzida.");
            confidence *= 0.85;
        }

        var draft = parseQuestMetadata(questMarkdown, request.level(), confidence);
        log.info("Quest draft finalizado — confiança: {}%", (int)(confidence * 100));
        return draft;
    }

    private double validateWithDomainSLM(String questMarkdown, String domain, double confidence) {
        try {
            String fixtures = extractFixtures(questMarkdown);
            if (fixtures.isBlank()) return confidence;

            String validationPrompt = buildValidationPrompt(domain, fixtures);
            String response = ollama.validate(validationPrompt);

            var result = mapper.readTree(response);
            boolean approved = result.path("aprovado").asBoolean(true);
            if (!approved) {
                log.warn("Meditron reprovou fixtures do domínio '{}' — confiança penalizada", domain);
                confidence *= 0.6;
            }
        } catch (Exception e) {
            log.warn("Validação Meditron falhou: {} — confiança levemente penalizada", e.getMessage());
            confidence *= 0.9;
        }
        return confidence;
    }

    private String buildGuardianPrompt(QuestRequest request) {
        return """
            Crie uma quest para o domínio '%s'.
            Tipo: %s
            Nível de dificuldade: %s
            Contexto adicional: %s

            Siga estritamente o schema do QUEST_FRAMEWORK.md.
            Inclua mínimo 3 fixtures com gabarito de domínio.
            Separe critérios em camadas: Técnico e Domínio.
            """.formatted(request.domain(), request.questType(), request.level(), request.context());
    }

    private String buildValidationPrompt(String domain, String fixtures) {
        return """
            Você é um validador clínico do domínio %s.
            Avalie se os fixtures abaixo são clinicamente plausíveis e se os gabaritos estão corretos.
            Responda em JSON: {"aprovado": true/false, "observacoes": "..."}

            Fixtures:
            %s
            """.formatted(domain, fixtures);
    }

    private String extractFixtures(String markdown) {
        var sb = new StringBuilder();
        boolean inFixture = false;
        for (String line : markdown.lines().toList()) {
            if (line.contains("```json")) { inFixture = true; continue; }
            if (line.contains("```") && inFixture) { inFixture = false; continue; }
            if (inFixture) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private QuestDraft parseQuestMetadata(String markdown, String level, double confidence) {
        String title = extractField(markdown, "###.*?—\\s*(.+?)\\s*`");
        String rarity = level;
        int xp = 200;
        List<String> skills = new ArrayList<>();

        Pattern xpPattern = Pattern.compile("\\+(\\d+)\\s*XP");
        Matcher xpMatcher = xpPattern.matcher(markdown);
        if (xpMatcher.find()) xp = Integer.parseInt(xpMatcher.group(1));

        Pattern skillPattern = Pattern.compile("\\*\\*Skills requeridas:\\*\\*\\s*(.+)");
        Matcher skillMatcher = skillPattern.matcher(markdown);
        if (skillMatcher.find()) {
            for (String s : skillMatcher.group(1).split(",")) {
                String skill = s.trim().replaceAll("`", "");
                if (!skill.isBlank()) skills.add(skill);
            }
        }

        return new QuestDraft(markdown, title, rarity, xp, skills, confidence,
            "claude-sonnet-4-6 + " + validatorModel);
    }

    private String extractField(String text, String regex) {
        var matcher = Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }
}