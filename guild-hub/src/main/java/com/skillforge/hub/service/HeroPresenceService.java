package com.skillforge.hub.service;

import com.skillforge.hub.web.HubDashboardController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HeroPresenceService {

    private static final Logger log = LoggerFactory.getLogger(HeroPresenceService.class);

    private final HubDashboardController dashboard;
    private final long timeoutMs;

    // heroId → last heartbeat timestamp
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();
    // heroId → hero name (for broadcast payload)
    private final Map<String, String> heroNames = new ConcurrentHashMap<>();
    // heroId → last known online state
    private final Set<String> onlineHeroes = ConcurrentHashMap.newKeySet();

    public HeroPresenceService(
            HubDashboardController dashboard,
            @Value("${skillforge.heartbeat.timeout-ms:180000}") long timeoutMs) {
        this.dashboard = dashboard;
        this.timeoutMs = timeoutMs;
    }

    public void recordHeartbeat(String heroId, String heroName) {
        lastSeen.put(heroId, Instant.now());
        heroNames.put(heroId, heroName);

        if (onlineHeroes.add(heroId)) {
            log.info("Herói {} agora está ONLINE.", heroId);
            dashboard.broadcast("HERO_ONLINE",
                    "{\"heroId\":\"%s\",\"heroName\":\"%s\"}".formatted(heroId, heroName));
        }
    }

    @Scheduled(fixedDelayString = "${skillforge.heartbeat.check-interval-ms:60000}")
    public void checkStaleHeroes() {
        Instant cutoff = Instant.now().minusMillis(timeoutMs);
        for (String heroId : Set.copyOf(onlineHeroes)) {
            Instant seen = lastSeen.get(heroId);
            if (seen != null && seen.isBefore(cutoff)) {
                onlineHeroes.remove(heroId);
                String name = heroNames.getOrDefault(heroId, heroId);
                log.info("Herói {} agora está OFFLINE (último heartbeat: {}).", heroId, seen);
                dashboard.broadcast("HERO_OFFLINE",
                        "{\"heroId\":\"%s\",\"heroName\":\"%s\"}".formatted(heroId, name));
            }
        }
    }

    public boolean isOnline(String heroId) {
        return onlineHeroes.contains(heroId);
    }

    public Map<String, Instant> getLastSeen() {
        return Map.copyOf(lastSeen);
    }

    public Set<String> getOnlineHeroes() {
        return Set.copyOf(onlineHeroes);
    }
}
