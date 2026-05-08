package com.skillforge.hub.web;

import com.skillforge.hub.service.HeroPresenceService;
import com.skillforge.hub.service.HeroRegistryService;
import com.skillforge.hub.service.QuestBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

@Controller
public class HubDashboardController {

    private final HeroRegistryService registry;
    private final QuestBoardService questBoard;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // @Lazy quebra a dependência circular: HeroPresenceService → HubDashboardController
    @Autowired @Lazy
    private HeroPresenceService presence;

    public HubDashboardController(HeroRegistryService registry, QuestBoardService questBoard) {
        this.registry = registry;
        this.questBoard = questBoard;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("heroCount",       registry.getHeroCount());
        model.addAttribute("totalXp",         registry.getTotalXp());
        model.addAttribute("openQuests",      questBoard.getOpenCount());
        model.addAttribute("completedQuests", questBoard.getCompletedCount());
        model.addAttribute("leaderboard",     registry.getLeaderboard());
        model.addAttribute("skillDist",       registry.getSkillDistribution());
        model.addAttribute("questsByRarity",  questBoard.getCountByRarity());
        model.addAttribute("lastFetchMs",     registry.getLastFetchMs());
        model.addAttribute("onlineHeroes",    presence != null ? presence.getOnlineHeroes() : Set.of());
        return "hub";
    }

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));

        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("GUILD_STATE")
                        .data("{\"heroes\":%d,\"openQuests\":%d,\"completedQuests\":%d,\"totalXp\":%d}"
                                .formatted(registry.getHeroCount(),
                                        questBoard.getOpenCount(),
                                        questBoard.getCompletedCount(),
                                        registry.getTotalXp())));
            } catch (IOException ignored) {}
        });

        return emitter;
    }

    public void broadcast(String eventName, String data) {
        List.copyOf(emitters).forEach(e -> {
            try {
                e.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception ex) {
                emitters.remove(e);
            }
        });
    }
}