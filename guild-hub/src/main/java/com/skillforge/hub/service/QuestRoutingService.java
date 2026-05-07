package com.skillforge.hub.service;

import com.skillforge.hub.amqp.ProblemMessage;
import com.skillforge.hub.amqp.ProblemPublisher;
import com.skillforge.hub.domain.GuildMember;
import com.skillforge.hub.domain.Quest;
import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuestRoutingService {

    private static final Logger log = LoggerFactory.getLogger(QuestRoutingService.class);

    private final HeroRegistryService registry;
    private final HeroPresenceService presence;
    private final QuestBoardService questBoard;
    private final ProblemPublisher publisher;
    private final HubDashboardController dashboard;

    public QuestRoutingService(HeroRegistryService registry,
                                HeroPresenceService presence,
                                QuestBoardService questBoard,
                                ProblemPublisher publisher,
                                HubDashboardController dashboard) {
        this.registry   = registry;
        this.presence   = presence;
        this.questBoard = questBoard;
        this.publisher  = publisher;
        this.dashboard  = dashboard;
    }

    public record HeroMatch(
        String heroId,
        String heroName,
        List<String> matchedSkills,
        boolean online
    ) {}

    public record QuestRoute(
        String questId,
        String questTitle,
        String rarity,
        int xpReward,
        List<String> requiredSkills,
        String routingKey,
        List<HeroMatch> candidates,   // todos com skill compatível
        HeroMatch elected             // melhor candidato online (null se nenhum online)
    ) {}

    /**
     * Retorna o mapa de roteamento para todas as quests abertas:
     * cada quest com seus candidatos (por skill) e o eleito (online com mais skills).
     */
    public List<QuestRoute> buildRoutingTable() {
        List<Quest> openQuests = questBoard.getOpenQuests();
        List<GuildMember> heroes = registry.getHeroes();
        Set<String> onlineIds = presence.getOnlineHeroes();

        return openQuests.stream()
            .map(q -> routeQuest(q, heroes, onlineIds))
            .collect(Collectors.toList());
    }

    /**
     * Despacha uma quest específica para o melhor hero disponível via AMQP.
     * Retorna o HeroMatch eleito ou empty se nenhum hero online.
     */
    public Optional<HeroMatch> dispatch(String questId, String submittedBy) {
        Quest quest = questBoard.getQuests().stream()
            .filter(q -> q.id().equals(questId))
            .findFirst()
            .orElse(null);

        if (quest == null) return Optional.empty();

        QuestRoute route = routeQuest(quest, registry.getHeroes(), presence.getOnlineHeroes());
        if (route.elected() == null) {
            log.warn("Nenhum hero online para quest {} (skills: {})", questId, quest.requiredSkills());
            return Optional.empty();
        }

        String routingKey = route.routingKey();
        publisher.publish(new ProblemMessage(
            quest.id(), quest.title(), quest.requiredSkills(), quest.xpReward(), submittedBy,
            quest.url(), quest.body()
        ), routingKey);

        log.info("Quest {} despachada para {} via {}", questId, route.elected().heroId(), routingKey);

        dashboard.broadcast("QUEST_ROUTED",
            ("{\"questId\":\"%s\",\"questTitle\":\"%s\",\"heroId\":\"%s\"," +
             "\"heroName\":\"%s\",\"routingKey\":\"%s\"}")
                .formatted(questId, quest.title(),
                    route.elected().heroId(), route.elected().heroName(), routingKey));

        return Optional.of(route.elected());
    }

    // "narrative design" e "narrative-design" devem ser tratados como a mesma skill
    private static String normalizeSkill(String skill) {
        return skill.toLowerCase().replace(' ', '-').trim();
    }

    private QuestRoute routeQuest(Quest quest, List<GuildMember> heroes, Set<String> onlineIds) {
        Set<String> normalizedQuestSkills = quest.requiredSkills().stream()
            .map(QuestRoutingService::normalizeSkill)
            .collect(Collectors.toSet());

        List<HeroMatch> candidates = heroes.stream()
            .map(h -> {
                List<String> matched = h.skills().stream()
                    .filter(s -> normalizedQuestSkills.contains(normalizeSkill(s)))
                    .toList();
                return matched.isEmpty() ? null
                    : new HeroMatch(h.heroId(), h.heroName(), matched, onlineIds.contains(h.heroId()));
            })
            .filter(Objects::nonNull)
            .sorted(Comparator
                .comparingInt((HeroMatch m) -> m.online() ? 0 : 1)
                .thenComparingInt(m -> -m.matchedSkills().size()))
            .toList();

        HeroMatch elected = candidates.stream().filter(HeroMatch::online).findFirst().orElse(null);

        // routing key normalizada: "narrative design" → "problem.narrative-design"
        String primarySkill = elected != null ? normalizeSkill(elected.matchedSkills().get(0))
            : (quest.requiredSkills().isEmpty() ? "general" : normalizeSkill(quest.requiredSkills().get(0)));
        String routingKey = "problem." + primarySkill;

        return new QuestRoute(
            quest.id(), quest.title(), quest.rarity().name(),
            quest.xpReward(), quest.requiredSkills(),
            routingKey, candidates, elected
        );
    }
}