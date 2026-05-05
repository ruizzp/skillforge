package com.skillforge.hub.service;

import com.skillforge.hub.domain.GuildMember;
import com.skillforge.hub.domain.HeroLevel;
import com.skillforge.hub.domain.LeaderboardEntry;
import com.skillforge.hub.github.GitHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class HeroRegistryService {

    private static final Logger log = LoggerFactory.getLogger(HeroRegistryService.class);

    private final GitHubClient github;
    private final AtomicReference<List<GuildMember>> heroes = new AtomicReference<>(List.of());
    private volatile long lastFetch = 0;

    public HeroRegistryService(GitHubClient github) {
        this.github = github;
        refresh();
    }

    public List<GuildMember> getHeroes() {
        return heroes.get();
    }

    public Optional<GuildMember> getHeroById(String heroId) {
        return heroes.get().stream()
                .filter(h -> h.heroId().equalsIgnoreCase(heroId))
                .findFirst();
    }

    public List<LeaderboardEntry> getLeaderboard() {
        List<GuildMember> sorted = heroes.get().stream()
                .sorted(Comparator.comparingInt(GuildMember::xp).reversed())
                .toList();

        return IntStream.range(0, sorted.size())
                .mapToObj(i -> new LeaderboardEntry(
                        i + 1,
                        sorted.get(i),
                        HeroLevel.fromLevel(sorted.get(i).level())))
                .toList();
    }

    public Map<String, Long> getSkillDistribution() {
        return heroes.get().stream()
                .flatMap(h -> h.skills().stream())
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
    }

    public long getTotalXp() {
        return heroes.get().stream().mapToLong(GuildMember::xp).sum();
    }

    public int getHeroCount() {
        return heroes.get().size();
    }

    public long getLastFetchMs() {
        return lastFetch;
    }

    @Scheduled(fixedDelay = 300_000)
    public void refresh() {
        List<GuildMember> fetched = github.fetchHeroes();
        heroes.set(fetched);
        lastFetch = System.currentTimeMillis();
        log.debug("Hero registry refreshed — {} heroes", fetched.size());
    }
}