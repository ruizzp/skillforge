package com.skillforge.hero.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.hero.domain.*;
import com.skillforge.hero.github.GitHubClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class GuildService {

    private final GitHubClient github;
    private final ObjectMapper mapper;

    private final AtomicReference<HeroManifest> manifest = new AtomicReference<>();
    private final AtomicReference<List<Quest>> quests = new AtomicReference<>(List.of());
    private final AtomicReference<List<GuildMember>> members = new AtomicReference<>(List.of());
    private volatile long lastFetch = 0;

    public GuildService(GitHubClient github, ObjectMapper mapper) {
        this.github = github;
        this.mapper = mapper;
        loadManifest();
        refresh();
    }

    public HeroManifest getManifest() {
        return manifest.get();
    }

    public HeroLevel getHeroLevel() {
        HeroManifest m = manifest.get();
        return m != null ? HeroLevel.fromLevel(m.level()) : HeroLevel.APPRENTICE;
    }

    public List<Quest> getVisibleQuests() {
        HeroLevel level = getHeroLevel();
        return quests.get().stream()
                .filter(q -> q.isVisibleTo(level))
                .collect(Collectors.toList());
    }

    public List<Quest> getLockedQuestTeasers() {
        HeroLevel level = getHeroLevel();
        return quests.get().stream()
                .filter(q -> !q.isVisibleTo(level))
                .map(q -> new Quest(
                        q.id(), "??? — " + q.rarity().name() + " Quest",
                        "Desbloqueado no nível " + nextLevelFor(q.rarity()) + ".",
                        q.rarity(), q.status(), List.of(), q.xpReward(),
                        "", "", q.number()))
                .collect(Collectors.toList());
    }

    public List<GuildMember> getMembers() {
        return members.get();
    }

    public List<String> getValidatedSkills() {
        String myId = manifest.get() != null ? manifest.get().heroId() : "";
        return members.get().stream()
                .filter(m -> m.heroId().equals(myId))
                .findFirst()
                .map(GuildMember::validatedSkills)
                .orElse(List.of());
    }

    public Map<QuestRarity, Long> getQuestCountByRarity() {
        return quests.get().stream()
                .collect(Collectors.groupingBy(Quest::rarity, Collectors.counting()));
    }

    public long getOpenQuestCount() {
        return quests.get().stream().filter(Quest::isAvailable).count();
    }

    public long getCompletedQuestCount() {
        return quests.get().stream().filter(Quest::isCompleted).count();
    }

    public boolean isGuildDataAvailable() {
        return !quests.get().isEmpty() || !members.get().isEmpty();
    }

    @Scheduled(fixedDelay = 300_000) // refresh every 5 minutes
    public void refresh() {
        quests.set(github.fetchQuests());
        members.set(github.fetchHeroes());
        lastFetch = System.currentTimeMillis();
    }

    public long getLastFetchMs() { return lastFetch; }

    private void loadManifest() {
        try {
            var resource = new ClassPathResource("manifest.json");
            manifest.set(mapper.readValue(resource.getInputStream(), HeroManifest.class));
        } catch (Exception e) {
            manifest.set(new HeroManifest(
                    "hero-unknown", "New Hero", "Unknown",
                    List.of(), "http://localhost:8081", "phi3:mini",
                    "Configure seu manifest.json para começar.", 1, 0));
        }
    }

    private String nextLevelFor(QuestRarity rarity) {
        return switch (rarity) {
            case COMMON -> "Apprentice (1)";
            case RARE -> "Journeyman (3)";
            case EPIC -> "Expert (5)";
            case LEGENDARY -> "Master (8)";
        };
    }
}