package com.skillforge.hero.amqp;

import com.skillforge.hero.service.GuildService;
import com.skillforge.hero.service.SolveService;
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
    private final SolveService solver;
    private final RabbitTemplate rabbit;
    private final String exchange;
    private static final String SOLUTIONS_KEY = "solution";

    public ProblemConsumer(GuildService guildService, SolveService solver,
                           RabbitTemplate rabbit,
                           @Value("${guild.amqp.exchange}") String exchange) {
        this.guildService = guildService;
        this.solver       = solver;
        this.rabbit       = rabbit;
        this.exchange     = exchange;
    }

    @RabbitListener(queues = "${guild.amqp.queue}")
    public void onProblem(ProblemMessage msg) {
        List<String> mySkills = guildService.getManifest().skills();
        boolean canSolve = msg.requiredSkills().isEmpty() ||
                msg.requiredSkills().stream().anyMatch(mySkills::contains);

        if (!canSolve) {
            log.debug("Quest {} requer {}. Skills: {}. Ignorando.", msg.questId(), msg.requiredSkills(), mySkills);
            return;
        }

        log.info("Resolvendo quest {} (skills: {})...", msg.questId(), msg.requiredSkills());

        var manifest = guildService.getManifest();
        var result   = solver.solve(msg.problem(), msg.requiredSkills());

        var solution = new SolutionMessage(
                msg.questId(), manifest.heroId(), manifest.heroName(),
                result.solution(), result.confidence(), result.model(), Instant.now());

        rabbit.convertAndSend(exchange, SOLUTIONS_KEY, solution);
        log.info("Solução postada — quest: {} | confiança: {}% | modelo: {}",
                msg.questId(), (int) (result.confidence() * 100), result.model());
    }
}