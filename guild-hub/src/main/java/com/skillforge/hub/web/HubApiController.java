package com.skillforge.hub.web;

import com.skillforge.hub.amqp.ProblemMessage;
import com.skillforge.hub.amqp.ProblemPublisher;
import com.skillforge.hub.domain.LeaderboardEntry;
import com.skillforge.hub.domain.Quest;
import com.skillforge.hub.domain.QuestRarity;
import com.skillforge.hub.github.GitHubClient;
import com.skillforge.hub.registration.ForkWatcher;
import com.skillforge.hub.service.HeroPresenceService;
import com.skillforge.hub.service.HeroRegistryService;
import com.skillforge.hub.service.QuestBoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HubApiController {

    private final HeroRegistryService registry;
    private final QuestBoardService questBoard;
    private final ForkWatcher forkWatcher;
    private final GitHubClient github;
    private final ProblemPublisher problemPublisher;
    private final HeroPresenceService presence;

    public HubApiController(HeroRegistryService registry, QuestBoardService questBoard,
                            ForkWatcher forkWatcher, GitHubClient github,
                            ProblemPublisher problemPublisher, HeroPresenceService presence) {
        this.registry = registry;
        this.questBoard = questBoard;
        this.forkWatcher = forkWatcher;
        this.github = github;
        this.problemPublisher = problemPublisher;
        this.presence = presence;
    }

    @GetMapping("/heroes")
    public ResponseEntity<?> heroes() {
        return ResponseEntity.ok(registry.getHeroes());
    }

    @GetMapping("/heroes/{heroId}")
    public ResponseEntity<?> hero(@PathVariable("heroId") String heroId) {
        return registry.getHeroById(heroId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> leaderboard() {
        return ResponseEntity.ok(registry.getLeaderboard());
    }

    @GetMapping("/quests")
    public ResponseEntity<List<Quest>> quests(
            @RequestParam(required = false) String rarity,
            @RequestParam(required = false, defaultValue = "all") String status) {

        List<Quest> result = rarity != null
                ? questBoard.getQuestsByRarity(QuestRarity.fromLabel(rarity))
                : questBoard.getQuests();

        result = switch (status) {
            case "open"      -> result.stream().filter(Quest::isAvailable).toList();
            case "completed" -> result.stream().filter(Quest::isCompleted).toList();
            default          -> result;
        };

        return ResponseEntity.ok(result);
    }

    @PostMapping("/quests")
    public ResponseEntity<?> createQuest(@RequestBody CreateQuestRequest req) {
        try {
            int number = questBoard.createQuest(
                    req.title(), req.body(), req.rarity(), req.xpReward(), req.requiredSkills());
            return ResponseEntity.ok(Map.of("issueNumber", number, "message", "Quest criada com sucesso."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "heroCount",        registry.getHeroCount(),
                "totalXp",          registry.getTotalXp(),
                "openQuests",       questBoard.getOpenCount(),
                "completedQuests",  questBoard.getCompletedCount(),
                "questsByRarity",   questBoard.getCountByRarity(),
                "skillDistribution",registry.getSkillDistribution(),
                "lastFetchMs",      registry.getLastFetchMs()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "heroes", registry.getHeroCount(),
                "quests", questBoard.getQuests().size()
        ));
    }

    @PostMapping("/heroes/{heroId}/skills/{skill}/validate")
    public ResponseEntity<?> validateSkill(
            @PathVariable("heroId") String heroId,
            @PathVariable("skill") String skill,
            @RequestParam(name = "validatedBy", defaultValue = "guild-master") String validatedBy) {

        return registry.getHeroById(heroId)
                .map(hero -> {
                    if (!hero.skills().contains(skill)) {
                        return ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "Skill '" + skill + "' não declarada por " + heroId));
                    }
                    if (hero.validatedSkills().contains(skill)) {
                        return ResponseEntity.ok()
                                .<Object>body(Map.of("message", "Skill '" + skill + "' já estava validada."));
                    }
                    try {
                        github.validateSkill(hero.issueNumber(), skill, validatedBy);
                        registry.refresh();
                        return ResponseEntity.ok()
                                .<Object>body(Map.of(
                                        "message", "Skill '" + skill + "' validada para " + heroId,
                                        "issueNumber", hero.issueNumber()
                                ));
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError()
                                .<Object>body(Map.of("error", e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/problems")
    public ResponseEntity<?> publishProblem(@RequestBody ProblemMessage msg) {
        try {
            problemPublisher.publish(msg);
            return ResponseEntity.ok(Map.of(
                    "message", "Problema publicado para a guilda.",
                    "questId", msg.questId(),
                    "requiredSkills", msg.requiredSkills()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/forks")
    public ResponseEntity<List<ForkWatcher.ForkStatus>> forks() {
        return ResponseEntity.ok(forkWatcher.scanAndRegister(false));
    }

    @GetMapping("/presence")
    public ResponseEntity<Map<String, Object>> presence() {
        var onlineIds = presence.getOnlineHeroes();
        var lastSeen = presence.getLastSeen().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toString()));
        return ResponseEntity.ok(Map.of(
                "online", onlineIds,
                "onlineCount", onlineIds.size(),
                "lastSeen", lastSeen
        ));
    }

    @PostMapping("/forks/scan")
    public ResponseEntity<List<ForkWatcher.ForkStatus>> triggerForkScan() {
        return ResponseEntity.ok(forkWatcher.scanAndRegister(true));
    }

    public record CreateQuestRequest(
            String title,
            String body,
            QuestRarity rarity,
            int xpReward,
            List<String> requiredSkills
    ) {}
}