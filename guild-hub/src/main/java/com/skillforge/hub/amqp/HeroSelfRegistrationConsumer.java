package com.skillforge.hub.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.hub.github.GitHubClient;
import com.skillforge.hub.service.HeroRegistryService;
import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HeroSelfRegistrationConsumer {

    private static final Logger log = LoggerFactory.getLogger(HeroSelfRegistrationConsumer.class);

    private final HeroRegistryService registry;
    private final GitHubClient github;
    private final HubDashboardController dashboard;
    private final ObjectMapper mapper;

    public HeroSelfRegistrationConsumer(HeroRegistryService registry,
                                        GitHubClient github,
                                        HubDashboardController dashboard,
                                        ObjectMapper mapper) {
        this.registry  = registry;
        this.github    = github;
        this.dashboard = dashboard;
        this.mapper    = mapper;
    }

    @RabbitListener(queues = AmqpConfig.HERO_REGISTRATION_QUEUE)
    public void onSelfRegistration(HeroSelfRegistrationMessage msg) {
        log.info("Auto-registro recebido — heroId: {} | heroName: {}", msg.heroId(), msg.heroName());

        // Skip if already in the registry (registered + validated)
        if (registry.getHeroById(msg.heroId()).isPresent()) {
            log.debug("Hero {} já registrado — ignorando auto-registro.", msg.heroId());
            return;
        }

        // Skip if a pending hero issue already exists for this heroId
        boolean alreadyPending = github.fetchPendingRegistrations().stream()
            .anyMatch(issue -> {
                String body = issue.path("body").asText("");
                return body.contains("\"" + msg.heroId() + "\"");
            });

        if (alreadyPending) {
            log.debug("Hero {} já possui issue pendente — ignorando auto-registro.", msg.heroId());
            return;
        }

        try {
            String manifestJson = mapper.writeValueAsString(Map.of(
                "heroId",     msg.heroId(),
                "heroName",   msg.heroName(),
                "heroClass",  msg.heroClass(),
                "skills",     msg.skills(),
                "specialty",  msg.specialty(),
                "endpoint",   msg.endpoint(),
                "model",      msg.model(),
                "githubLogin", msg.heroId()
            ));

            String title = "[Hero Registration] " + msg.heroName() + " (internal)";
            int issueNumber = github.createHeroIssue(title, manifestJson);

            log.info("Issue #{} criada para hero interno '{}' via auto-registro.", issueNumber, msg.heroId());

            registry.refresh();

            dashboard.broadcast("FORK_DISCOVERED",
                "{\"heroId\":\"%s\",\"heroName\":\"%s\",\"forkOwner\":\"internal\",\"issueNumber\":%d}"
                    .formatted(msg.heroId(), msg.heroName(), issueNumber));

        } catch (Exception e) {
            log.error("Falha ao criar issue de auto-registro para {}: {}", msg.heroId(), e.getMessage());
        }
    }
}