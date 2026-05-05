package com.skillforge.hero.web;

import com.skillforge.hero.domain.HeroManifest;
import com.skillforge.hero.service.GuildService;
import com.skillforge.hero.service.SolveService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HeroApiController {

    private final GuildService guild;
    private final SolveService solver;
    private final long startTime = System.currentTimeMillis();

    public HeroApiController(GuildService guild, SolveService solver) {
        this.guild  = guild;
        this.solver = solver;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "heroId", guild.getManifest().heroId(),
                "status", "UP",
                "uptime", (System.currentTimeMillis() - startTime) / 1000,
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/manifest")
    public HeroManifest manifest() {
        return guild.getManifest();
    }

    @GetMapping("/guild/status")
    public Map<String, Object> guildStatus() {
        return Map.of(
                "heroes", guild.getMembers().size(),
                "openQuests", guild.getOpenQuestCount(),
                "completedQuests", guild.getCompletedQuestCount(),
                "heroLevel", guild.getHeroLevel().name(),
                "lastFetch", Instant.ofEpochMilli(guild.getLastFetchMs()).toString()
        );
    }

    record SolveRequest(String problem, List<String> requiredSkills) {
        SolveRequest { if (requiredSkills == null) requiredSkills = List.of(); }
    }

    @PostMapping("/solve")
    public Map<String, Object> solve(@RequestBody SolveRequest req) {
        var result = solver.solve(req.problem(), req.requiredSkills());
        return Map.of(
                "heroId",     guild.getManifest().heroId(),
                "solution",   result.solution(),
                "confidence", result.confidence(),
                "model",      result.model(),
                "solvedAt",   Instant.now().toString()
        );
    }
}