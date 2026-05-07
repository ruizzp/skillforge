package com.skillforge.marketing.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.marketing.domain.PitchDraft;
import com.skillforge.marketing.domain.ProjectBrief;
import com.skillforge.marketing.service.GitHubCommentService;
import com.skillforge.marketing.service.PitchGeneratorService;
import com.skillforge.marketing.service.PitchValidatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class PitchListener {

    private static final Logger log = LoggerFactory.getLogger(PitchListener.class);
    private static final String SOLUTION_ROUTING_KEY = "solution.pitch-design";

    private final PitchGeneratorService generator;
    private final PitchValidatorService validator;
    private final GitHubCommentService github;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    @Value("${skillforge.hero.id:hero-marketing}")
    private String heroId;

    @Value("${skillforge.hero.name:Hero Marketing}")
    private String heroName;

    @Value("${guild.amqp.exchange:skillforge}")
    private String exchange;

    public PitchListener(PitchGeneratorService generator,
                         PitchValidatorService validator,
                         GitHubCommentService github,
                         RabbitTemplate rabbit,
                         ObjectMapper mapper) {
        this.generator = generator;
        this.validator = validator;
        this.github    = github;
        this.rabbit    = rabbit;
        this.mapper    = mapper;
    }

    @RabbitListener(queues = AmqpConfig.QUEUE)
    public void onProblem(ProblemMessage msg) {
        log.info("Problema recebido — quest: {} | skills: {}", msg.questId(), msg.requiredSkills());
        try {
            // Constrói o brief a partir do contexto da quest
            ProjectBrief brief = buildBrief(msg);

            Map<String, String> docs = generator.generate(brief);
            PitchDraft draft = validator.validate(docs.get("guildPitch"), docs.get("investorOnePager"));

            log.info("Pitch gerado — confidence: {:.0f}% | valid: {}", draft.confidence() * 100, draft.valid());

            // Posta os artefatos como comentário na issue da quest
            github.postArtifacts(msg.questUrl(), msg.questId(), draft);

            // Envia SolutionMessage de volta ao hub
            String summary = buildSummary(draft);
            var solution = new SolutionMessage(
                msg.questId(), heroId, heroName,
                summary, draft.confidence(),
                "claude-sonnet-4-6", Instant.now()
            );
            rabbit.convertAndSend(exchange, SOLUTION_ROUTING_KEY, solution);
            log.info("Solução publicada para quest {} — confidence: {:.0f}%", msg.questId(), draft.confidence() * 100);

        } catch (Exception e) {
            log.error("Falha ao processar quest {}: {}", msg.questId(), e.getMessage());
        }
    }

    private ProjectBrief buildBrief(ProblemMessage msg) {
        // Usa o título da quest como nome do projeto e o corpo como contexto
        // O PitchGeneratorService usa Claude para interpretar o contexto livre
        String context = msg.questBody() != null && !msg.questBody().isBlank()
            ? msg.questBody()
            : msg.problem();

        return new ProjectBrief(
            msg.questId(),
            msg.problem(),       // título da quest como nome do projeto
            extractProblemStatement(context),
            List.of("dev senior", "CTO", "angel investor"),
            extractDifferentiator(context),
            "Plataforma funcional com heroes de IA operacionais",
            "Freemium: quests públicas gratuitas + guild membership pago",
            "Entre na guild de fundadores",
            context
        );
    }

    private String extractProblemStatement(String context) {
        // Extrai a primeira frase relevante do contexto como problem statement
        // Claude vai interpretar o contexto completo de qualquer forma
        if (context == null || context.isBlank()) return "Problema não especificado";
        String[] lines = context.split("\n");
        for (String line : lines) {
            line = line.strip().replaceAll("^#+\\s*", "").replaceAll("\\*+", "");
            if (line.length() > 20 && !line.startsWith("#") && !line.startsWith(">")) {
                return line.length() > 200 ? line.substring(0, 200) : line;
            }
        }
        return context.length() > 200 ? context.substring(0, 200) : context;
    }

    private String extractDifferentiator(String context) {
        if (context == null) return "A definir";
        String lower = context.toLowerCase();
        int idx = lower.indexOf("diferenci");
        if (idx < 0) idx = lower.indexOf("diferenc");
        if (idx >= 0) {
            int end = Math.min(idx + 300, context.length());
            return context.substring(idx, end).replaceAll("\n", " ").trim();
        }
        return "Skills validadas por desafios reais com IA, registradas no GitHub";
    }

    private String buildSummary(PitchDraft draft) {
        return "Guild Pitch e Investor One-Pager gerados. " +
               "Score médio: %.1f/10. %s".formatted(
                   (draft.clareza() + draft.dor() + draft.diferencial() + draft.credibilidade()) / 4.0,
                   draft.veredicto()
               );
    }
}