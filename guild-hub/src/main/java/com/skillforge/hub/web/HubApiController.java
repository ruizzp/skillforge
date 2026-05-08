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
import com.skillforge.hub.service.QuestRoutingService;
import org.springframework.beans.factory.annotation.Value;
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
    private final QuestRoutingService routing;

    @Value("${guild.amqp.exchange}")
    private String amqpExchange;

    @Value("${guild.amqp.queue}")
    private String amqpQueue;

    @Value("${spring.rabbitmq.addresses:not-configured}")
    private String amqpAddresses;

    public HubApiController(HeroRegistryService registry, QuestBoardService questBoard,
                            ForkWatcher forkWatcher, GitHubClient github,
                            ProblemPublisher problemPublisher, HeroPresenceService presence,
                            QuestRoutingService routing) {
        this.registry = registry;
        this.questBoard = questBoard;
        this.forkWatcher = forkWatcher;
        this.github = github;
        this.problemPublisher = problemPublisher;
        this.presence = presence;
        this.routing = routing;
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

    @GetMapping("/heroes/{heroId}/activity")
    public ResponseEntity<?> heroActivity(@PathVariable("heroId") String heroId) {
        return registry.getHeroById(heroId)
                .<ResponseEntity<?>>map(hero -> ResponseEntity.ok(github.fetchHeroActivity(hero.issueNumber())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/quests/{questId}/comments")
    public ResponseEntity<?> questComments(@PathVariable("questId") String questId) {
        return questBoard.getQuests().stream()
            .filter(q -> q.id().equals(questId))
            .<ResponseEntity<?>>map(q -> ResponseEntity.ok(github.fetchIssueComments(q.number())))
            .findFirst()
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/heroes/{heroId}/quests")
    public ResponseEntity<?> heroQuests(@PathVariable("heroId") String heroId) {
        if (registry.getHeroById(heroId).isEmpty()) return ResponseEntity.notFound().build();
        var all = questBoard.getQuests();
        var inProgress    = all.stream()
            .filter(q -> q.isAvailable() && heroId.equals(q.assignedTo()) && q.solvers().isEmpty())
            .toList();
        var pendingReview = all.stream()
            .filter(q -> q.isAvailable() && q.solvers().contains(heroId))
            .toList();
        return ResponseEntity.ok(Map.of("inProgress", inProgress, "pendingReview", pendingReview));
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
            case "open"           -> result.stream().filter(Quest::isAvailable).toList();
            case "completed"      -> result.stream().filter(Quest::isCompleted).toList();
            case "pending-review" -> result.stream()
                .filter(q -> q.isAvailable() && !q.solvers().isEmpty()).toList();
            default               -> result;
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

    @DeleteMapping("/heroes/{heroId}/skills/{skill}/validate")
    public ResponseEntity<?> resetSkillValidation(
            @PathVariable("heroId") String heroId,
            @PathVariable("skill") String skill) {

        return registry.getHeroById(heroId)
                .map(hero -> {
                    if (!hero.validatedSkills().contains(skill)) {
                        return ResponseEntity.ok()
                                .<Object>body(Map.of("message", "Skill '" + skill + "' não estava validada."));
                    }
                    try {
                        github.removeSkillValidation(hero.issueNumber(), skill);
                        registry.refresh();
                        return ResponseEntity.ok()
                                .<Object>body(Map.of("message", "Validação de '" + skill + "' removida."));
                    } catch (Exception e) {
                        return ResponseEntity.internalServerError()
                                .<Object>body(Map.of("error", e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/heroes/{heroId}/skills/{skill}/validate")
    public ResponseEntity<?> validateSkill(
            @PathVariable("heroId") String heroId,
            @PathVariable("skill") String skill) {

        return registry.getHeroById(heroId)
                .map(hero -> {
                    if (!hero.skills().contains(skill)) {
                        return ResponseEntity.badRequest()
                                .<Object>body(Map.of("error", "Skill '" + skill + "' não declarada por " + heroId));
                    }
                    if (hero.validatedSkills().contains(skill)) {
                        return ResponseEntity.ok()
                                .<Object>body(Map.of("message", "já validada"));
                    }
                    // Envia desafio real ao herói via AMQP; SolutionConsumer valida ao receber resposta
                    String questId = "probe:" + skill + ":" + heroId;
                    ProblemMessage probe = new ProblemMessage(
                            questId,
                            "Demonstre seu domínio de " + skill + " com um exemplo prático e objetivo.",
                            List.of(skill),
                            0,
                            "skill-probe",
                            "",
                            ""
                    );
                    problemPublisher.publish(probe);
                    return ResponseEntity.accepted()
                            .<Object>body(Map.of("questId", questId, "message", "desafio enviado"));
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

    @GetMapping("/routing")
    public ResponseEntity<?> routingTable() {
        return ResponseEntity.ok(routing.buildRoutingTable());
    }

    @PostMapping("/routing/{questId}/dispatch")
    public ResponseEntity<?> dispatch(@PathVariable String questId,
                                      @RequestParam(defaultValue = "hub") String submittedBy) {
        return routing.dispatch(questId, submittedBy)
            .map(hero -> ResponseEntity.ok(Map.of(
                "dispatched", true,
                "heroId",     hero.heroId(),
                "heroName",   hero.heroName(),
                "matchedSkills", hero.matchedSkills()
            )))
            .orElseGet(() -> ResponseEntity.ok(Map.of(
                "dispatched", false,
                "reason",     "Nenhum hero online com skills compatíveis"
            )));
    }

    @GetMapping("/amqp-info")
    public ResponseEntity<?> amqpInfo() {
        return ResponseEntity.ok(Map.of(
            "exchange",      amqpExchange,
            "problemsQueue", amqpQueue,
            "addresses",     amqpAddresses
        ));
    }

    @PostMapping("/routing/{questId}/approve")
    public ResponseEntity<?> approve(@PathVariable String questId,
                                     @RequestParam(required = false) String heroId) {
        var result = routing.approve(questId, heroId);
        return result.approved()
            ? ResponseEntity.ok(result)
            : ResponseEntity.unprocessableEntity().body(result);
    }

    @PostMapping("/routing/{questId}/rereview")
    public ResponseEntity<?> rereview(@PathVariable String questId,
                                      @RequestBody(required = false) RereviewRequest body) {
        String heroId   = body != null ? body.heroId()   : null;
        String solution = body != null ? body.solution() : null;
        var result = routing.rereview(questId, heroId, solution);
        return result.queued()
            ? ResponseEntity.accepted().body(result)
            : ResponseEntity.unprocessableEntity().body(result);
    }

    public record RereviewRequest(String heroId, String solution) {}

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh() {
        registry.refresh();
        questBoard.refresh();
        return ResponseEntity.ok(Map.of(
            "heroes",         registry.getHeroes().size(),
            "openQuests",     questBoard.getOpenQuests().size(),
            "completedQuests", questBoard.getCompletedQuests().size(),
            "totalXp",        registry.getTotalXp()
        ));
    }

    public record CreateQuestRequest(
            String title,
            String body,
            QuestRarity rarity,
            int xpReward,
            List<String> requiredSkills
    ) {}
}