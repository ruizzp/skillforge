package com.skillforge.hub.amqp;

import com.skillforge.hub.domain.Quest;
import com.skillforge.hub.github.GitHubClient;
import com.skillforge.hub.service.HeroRegistryService;
import com.skillforge.hub.service.QuestBoardService;
import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReviewResultConsumer.class);

    private final HeroRegistryService registry;
    private final QuestBoardService questBoard;
    private final GitHubClient github;
    private final HubDashboardController dashboard;
    private final RabbitTemplate rabbit;

    @Value("${guild.amqp.exchange}")
    private String exchange;

    @Value("${guild.reviewer.max-revisions:3}")
    private int maxRevisions;

    public ReviewResultConsumer(HeroRegistryService registry, QuestBoardService questBoard,
                                GitHubClient github, HubDashboardController dashboard,
                                RabbitTemplate rabbit) {
        this.registry   = registry;
        this.questBoard = questBoard;
        this.github     = github;
        this.dashboard  = dashboard;
        this.rabbit     = rabbit;
    }

    @RabbitListener(queues = AmqpConfig.REVIEW_RESULTS_QUEUE)
    public void onReviewResult(ReviewResultMessage result) {
        log.info("Resultado de revisão recebido — quest: {} | herói: {} | aprovado: {} | score: {}",
                result.questId(), result.heroId(), result.approved(), result.reviewScore());

        questBoard.refresh();
        Quest quest = questBoard.getQuests().stream()
                .filter(q -> q.id().equals(result.questId()))
                .findFirst()
                .orElse(null);

        if (quest == null) {
            log.warn("Quest {} não encontrada no board após refresh. Ignorando resultado de revisão.",
                    result.questId());
            return;
        }

        if (result.approved()) {
            handleApproved(quest, result);
        } else if (result.revisionCount() < maxRevisions) {
            handleRevisionRequested(quest, result);
        } else {
            handleRevisionLimitReached(quest, result);
        }
    }

    private void handleApproved(Quest quest, ReviewResultMessage result) {
        try {
            github.setQuestStatus(quest.number(), "pending-review");
            github.addLabel(quest.number(), "solved-by:" + result.heroId());
            log.info("Quest {} aprovada pelo revisor — encaminhando para revisão humana (herói: {})",
                    quest.id(), result.heroId());
        } catch (Exception e) {
            log.error("Falha ao atualizar estado da quest {} após aprovação do revisor: {}",
                    quest.id(), e.getMessage());
        }

        dashboard.broadcast("SOLUTION_REVIEWED",
                "{\"questId\":\"%s\",\"heroId\":\"%s\",\"approved\":true,\"score\":%.2f}"
                        .formatted(result.questId(), result.heroId(), result.reviewScore()));
    }

    private void handleRevisionRequested(Quest quest, ReviewResultMessage result) {
        int nextAttempt = result.revisionCount() + 1;

        try {
            github.setRevisionCount(quest.number(), nextAttempt);

            String comment = "## Revisão Solicitada — Tentativa %d/%d\n\n%s"
                    .formatted(nextAttempt, maxRevisions, result.feedback());
            github.postComment(quest.number(), comment);
            log.info("Revisão solicitada para quest {} — tentativa {}/{} (herói: {})",
                    quest.id(), nextAttempt, maxRevisions, result.heroId());
        } catch (Exception e) {
            log.error("Falha ao registrar revisão para quest {}: {}", quest.id(), e.getMessage());
        }

        // Re-publish ProblemMessage with feedback embedded in questBody
        String updatedBody = quest.body() + "\n\n---\n## Feedback da Revisão (tentativa %d/%d)\n\n%s"
                .formatted(nextAttempt, maxRevisions, result.feedback());

        String primarySkill = quest.requiredSkills().isEmpty()
                ? "general"
                : normalizeSkill(quest.requiredSkills().get(0));
        String routingKey = "problem." + primarySkill;

        ProblemMessage problem = new ProblemMessage(
                quest.id(),
                quest.title(),
                quest.requiredSkills(),
                quest.xpReward(),
                result.heroId(),
                quest.url(),
                updatedBody
        );

        rabbit.convertAndSend(exchange, routingKey, problem);
        log.info("Quest {} re-despachada com feedback via '{}' (tentativa {}/{})",
                quest.id(), routingKey, nextAttempt, maxRevisions);

        dashboard.broadcast("REVISION_REQUESTED",
                "{\"questId\":\"%s\",\"heroId\":\"%s\",\"attempt\":%d,\"feedback\":\"%s\"}"
                        .formatted(result.questId(), result.heroId(), nextAttempt,
                                result.feedback().replace("\"", "\\\"").replace("\n", "\\n")));
    }

    private void handleRevisionLimitReached(Quest quest, ReviewResultMessage result) {
        try {
            github.setQuestStatus(quest.number(), "pending-review");
            github.addLabel(quest.number(), "solved-by:" + result.heroId());
            github.addLabel(quest.number(), "revision-limit");
            log.warn("Quest {} atingiu limite de revisões ({}) — escalando para revisão humana (herói: {})",
                    quest.id(), maxRevisions, result.heroId());
        } catch (Exception e) {
            log.error("Falha ao escalar quest {} para revisão humana: {}", quest.id(), e.getMessage());
        }

        dashboard.broadcast("SOLUTION_REVIEWED",
                "{\"questId\":\"%s\",\"heroId\":\"%s\",\"approved\":false,\"revisionLimit\":true}"
                        .formatted(result.questId(), result.heroId()));
    }

    private static String normalizeSkill(String skill) {
        return skill.toLowerCase().replace(' ', '-').trim();
    }
}