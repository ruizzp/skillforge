package com.skillforge.hub.amqp;

import com.skillforge.hub.service.HeroRegistryService;
import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SolutionConsumer {

    private static final Logger log = LoggerFactory.getLogger(SolutionConsumer.class);

    private final HeroRegistryService registry;
    private final HubDashboardController dashboard;

    public SolutionConsumer(HeroRegistryService registry, HubDashboardController dashboard) {
        this.registry = registry;
        this.dashboard = dashboard;
    }

    @RabbitListener(queues = AmqpConfig.SOLUTIONS_QUEUE)
    public void onSolution(SolutionMessage msg) {
        log.info("Solução recebida — quest: {} | herói: {} | confiança: {}%",
                msg.questId(), msg.heroId(), (int) (msg.confidence() * 100));

        // award XP to the hero (simple: +xpReward from quest, here fixed for now)
        registry.getHeroById(msg.heroId()).ifPresent(hero -> {
            log.info("Herói {} resolveu quest {}. XP a ser creditado via quest board.",
                    hero.heroName(), msg.questId());
        });

        // broadcast to dashboard in real time
        dashboard.broadcast("SOLUTION_RECEIVED",
                "{\"questId\":\"%s\",\"heroId\":\"%s\",\"heroName\":\"%s\",\"confidence\":%.2f}"
                        .formatted(msg.questId(), msg.heroId(), msg.heroName(), msg.confidence()));

        log.debug("Solução completa de {}:\n{}", msg.heroId(), msg.solution());
    }
}