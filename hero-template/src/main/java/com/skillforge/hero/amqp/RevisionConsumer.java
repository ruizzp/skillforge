package com.skillforge.hero.amqp;

import com.skillforge.hero.github.GitHubClient;
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
public class RevisionConsumer {

    private static final Logger log = LoggerFactory.getLogger(RevisionConsumer.class);

    private final GuildService guildService;
    private final SolveService solver;
    private final RabbitTemplate rabbit;
    private final GitHubClient github;
    private final String exchange;

    public RevisionConsumer(GuildService guildService, SolveService solver,
                            RabbitTemplate rabbit, GitHubClient github,
                            @Value("${guild.amqp.exchange}") String exchange) {
        this.guildService = guildService;
        this.solver       = solver;
        this.rabbit       = rabbit;
        this.github       = github;
        this.exchange     = exchange;
    }

    @RabbitListener(queues = "${skillforge.hero.id}.revisions")
    public void onRevision(RevisionMessage msg) {
        log.info("Revisão solicitada — quest: {} | tentativa: {}/{}", msg.questId(), msg.revisionNumber(), msg.issueNumber());

        String questBody    = github.fetchIssueBody(msg.issueNumber());
        List<String> comments = github.fetchIssueComments(msg.issueNumber());
        String feedback     = extractReviewerFeedback(comments);

        String problem = questBody.isBlank() ? msg.questId() : questBody;
        if (!feedback.isBlank()) {
            problem += "\n\n---\n## Feedback do Revisor (tentativa %d)\n\n%s"
                    .formatted(msg.revisionNumber(), feedback);
        }

        var manifest = guildService.getManifest();
        var result   = solver.solve(problem, msg.requiredSkills());

        var solution = new SolutionMessage(
                msg.questId(), manifest.heroId(), manifest.heroName(),
                result.solution(), result.confidence(), result.model(), Instant.now());

        String primarySkill = msg.requiredSkills().isEmpty() ? "general" : msg.requiredSkills().get(0);
        rabbit.convertAndSend(exchange, "solution." + primarySkill, solution);

        log.info("Solução revisada publicada — quest: {} | tentativa: {} | confiança: {}% | modelo: {}",
                msg.questId(), msg.revisionNumber(), (int) (result.confidence() * 100), result.model());
    }

    private String extractReviewerFeedback(List<String> comments) {
        for (int i = comments.size() - 1; i >= 0; i--) {
            String c = comments.get(i);
            if (c.startsWith("## Revisão Solicitada")) {
                int bodyStart = c.indexOf('\n');
                return bodyStart > 0 ? c.substring(bodyStart).strip() : c;
            }
        }
        return "";
    }
}