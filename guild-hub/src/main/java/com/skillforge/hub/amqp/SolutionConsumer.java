package com.skillforge.hub.amqp;

import com.skillforge.hub.domain.GuildMember;
import com.skillforge.hub.domain.Quest;
import com.skillforge.hub.github.GitHubClient;
import com.skillforge.hub.service.HeroRegistryService;
import com.skillforge.hub.service.QuestBoardService;
import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SolutionConsumer {

    private static final Logger log = LoggerFactory.getLogger(SolutionConsumer.class);

    private final HeroRegistryService registry;
    private final QuestBoardService questBoard;
    private final GitHubClient github;
    private final HubDashboardController dashboard;

    @Value("${skillforge.skill-validation.confidence-threshold:0.75}")
    private double confidenceThreshold;

    public SolutionConsumer(HeroRegistryService registry, QuestBoardService questBoard,
                            GitHubClient github, HubDashboardController dashboard) {
        this.registry = registry;
        this.questBoard = questBoard;
        this.github = github;
        this.dashboard = dashboard;
    }

    @RabbitListener(queues = AmqpConfig.SOLUTIONS_QUEUE)
    public void onSolution(SolutionMessage msg) {
        log.info("Solução recebida — quest: {} | herói: {} | confiança: {}%",
                msg.questId(), msg.heroId(), (int) (msg.confidence() * 100));

        registry.getHeroById(msg.heroId()).ifPresent(hero -> {
            log.info("Herói {} resolveu quest {}. XP a ser creditado via quest board.",
                    hero.heroName(), msg.questId());
        });

        dashboard.broadcast("SOLUTION_RECEIVED",
                "{\"questId\":\"%s\",\"heroId\":\"%s\",\"heroName\":\"%s\",\"confidence\":%.2f}"
                        .formatted(msg.questId(), msg.heroId(), msg.heroName(), msg.confidence()));

        log.debug("Solução completa de {}:\n{}", msg.heroId(), msg.solution());

        if (!msg.questId().startsWith("probe:")) {
            questBoard.getQuests().stream()
                .filter(q -> q.id().equals(msg.questId()))
                .findFirst()
                .ifPresent(quest -> {
                    try { github.setQuestStatus(quest.number(), "pending-review"); }
                    catch (Exception e) { log.warn("Não foi possível marcar pending-review na quest {}: {}", msg.questId(), e.getMessage()); }
                });
        }

        if (msg.confidence() >= confidenceThreshold) {
            autoValidateSkills(msg);
        }
    }

    private void autoValidateSkills(SolutionMessage msg) {
        GuildMember hero = registry.getHeroById(msg.heroId()).orElse(null);
        if (hero == null) {
            log.warn("Auto-validação ignorada: herói {} não encontrado.", msg.heroId());
            return;
        }

        // probe:java:heroId — desafio enviado pelo endpoint /validate; usa skill do questId diretamente
        List<String> skillsToValidate;
        if (msg.questId().startsWith("probe:")) {
            String[] parts = msg.questId().split(":", 3);
            skillsToValidate = parts.length >= 2 ? List.of(parts[1]) : List.of();
        } else {
            Quest quest = questBoard.getQuests().stream()
                    .filter(q -> q.id().equals(msg.questId()))
                    .findFirst()
                    .orElse(null);
            if (quest == null || quest.requiredSkills().isEmpty()) {
                log.debug("Auto-validação ignorada: quest {} sem requiredSkills.", msg.questId());
                return;
            }
            skillsToValidate = quest.requiredSkills();
        }

        List<String> validated = new ArrayList<>();
        for (String skill : skillsToValidate) {
            if (!hero.skills().contains(skill)) continue;
            if (hero.validatedSkills().contains(skill)) continue;
            try {
                github.validateSkill(hero.issueNumber(), skill, "guild-bot");
                validated.add(skill);
                log.info("Skill '{}' validada para {} via {}.", skill, msg.heroId(), msg.questId());
            } catch (Exception e) {
                log.error("Falha ao validar skill '{}' para {}: {}", skill, msg.heroId(), e.getMessage());
            }
        }

        // XP da quest (apenas quests reais — probes já creditam via validateSkill)
        if (!msg.questId().startsWith("probe:") && !validated.isEmpty()) {
            questBoard.getQuests().stream()
                    .filter(q -> q.id().equals(msg.questId()) && q.xpReward() > 0)
                    .findFirst()
                    .ifPresent(quest -> {
                        try {
                            github.addXp(hero.issueNumber(), quest.xpReward());
                            log.info("Quest XP creditado: +{} para {} (quest {}).",
                                    quest.xpReward(), msg.heroId(), msg.questId());
                        } catch (Exception e) {
                            log.error("Falha ao creditar quest XP para {}: {}", msg.heroId(), e.getMessage());
                        }
                    });
        }

        if (!validated.isEmpty()) {
            registry.refresh();
            String skillsJson = validated.stream()
                    .map(s -> "\"" + s + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            int newXp = registry.getHeroById(msg.heroId()).map(h -> h.xp()).orElse(0);
            dashboard.broadcast("SKILL_AUTO_VALIDATED",
                    "{\"heroId\":\"%s\",\"questId\":\"%s\",\"skills\":%s,\"xp\":%d,\"totalXp\":%d}"
                            .formatted(msg.heroId(), msg.questId(), skillsJson, newXp, registry.getTotalXp()));
        }
    }
}
