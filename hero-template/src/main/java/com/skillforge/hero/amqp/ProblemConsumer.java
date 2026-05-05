package com.skillforge.hero.amqp;

import com.skillforge.hero.service.GuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ProblemConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProblemConsumer.class);

    private final GuildService guildService;
    private final RabbitTemplate rabbit;
    private final String exchange;
    private final String solutionsRoutingKey = "solution";

    public ProblemConsumer(GuildService guildService,
                           RabbitTemplate rabbit,
                           @Value("${guild.amqp.exchange}") String exchange) {
        this.guildService = guildService;
        this.rabbit = rabbit;
        this.exchange = exchange;
    }

    @RabbitListener(queues = "${guild.amqp.queue}")
    public void onProblem(ProblemMessage msg) {
        List<String> mySkills = guildService.getManifest().skills();
        List<String> required = msg.requiredSkills();

        boolean canSolve = required.isEmpty() ||
                required.stream().anyMatch(mySkills::contains);

        if (!canSolve) {
            log.debug("Quest {} requer {}. Minhas skills: {}. Ignorando.",
                    msg.questId(), required, mySkills);
            return;
        }

        log.info("Resolvendo quest {} (skills: {})...", msg.questId(), required);

        SolutionMessage solution = solve(msg);
        rabbit.convertAndSend(exchange, solutionsRoutingKey, solution);

        log.info("Solução postada para quest {} — confiança: {}%",
                msg.questId(), (int) (solution.confidence() * 100));
    }

    private SolutionMessage solve(ProblemMessage msg) {
        var manifest = guildService.getManifest();

        // Placeholder — substitua pela chamada real ao Ollama:
        // POST http://localhost:11434/api/generate
        // { "model": manifest.model(), "prompt": msg.problem(), "stream": false }
        String solution = """
                Análise de %s:

                Problema recebido: %s

                Com base nas minhas skills em %s, minha recomendação é:
                [Implemente a chamada ao Ollama aqui — veja /api/solve]

                Herói: %s (%s)
                """.formatted(
                msg.questId(),
                msg.problem(),
                String.join(", ", manifest.skills()),
                manifest.heroName(),
                manifest.heroId());

        return new SolutionMessage(
                msg.questId(),
                manifest.heroId(),
                manifest.heroName(),
                solution,
                0.7,
                manifest.model(),
                Instant.now()
        );
    }
}