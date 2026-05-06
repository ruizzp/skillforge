package com.skillforge.hero.web;

import com.skillforge.hero.domain.HeroLevel;
import com.skillforge.hero.service.GuildService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

@Controller
public class GuildController {

    private final GuildService guild;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public GuildController(GuildService guild) {
        this.guild = guild;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        var manifest = guild.getManifest();
        var level = guild.getHeroLevel();

        model.addAttribute("hero", manifest);
        model.addAttribute("heroLevel", level);
        model.addAttribute("isApprentice", level == HeroLevel.APPRENTICE);
        model.addAttribute("isJourneymanPlus", level.level >= HeroLevel.JOURNEYMAN.level);
        model.addAttribute("isExpertPlus", level.level >= HeroLevel.EXPERT.level);
        model.addAttribute("isMasterPlus", level.level >= HeroLevel.MASTER.level);

        model.addAttribute("validatedSkills", guild.getValidatedSkills());
        model.addAttribute("heroXp", guild.getOwnXp());
        model.addAttribute("members", guild.getMembers());
        model.addAttribute("memberCount", guild.getMembers().size());
        model.addAttribute("openQuests", guild.getOpenQuestCount());
        model.addAttribute("completedQuests", guild.getCompletedQuestCount());
        model.addAttribute("guildDataAvailable", guild.isGuildDataAvailable());
        model.addAttribute("lastFetchMs", guild.getLastFetchMs());

        return "dashboard";
    }

    @GetMapping("/quests")
    public String quests(Model model) {
        var level = guild.getHeroLevel();
        model.addAttribute("heroLevel", level);
        model.addAttribute("visibleQuests", guild.getVisibleQuests());
        model.addAttribute("lockedTeasers", guild.getLockedQuestTeasers());
        model.addAttribute("questsByRarity", guild.getQuestCountByRarity());
        model.addAttribute("isJourneymanPlus", level.level >= HeroLevel.JOURNEYMAN.level);
        model.addAttribute("isExpertPlus", level.level >= HeroLevel.EXPERT.level);
        return "quests";
    }

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));

        // send current guild state on connect
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("GUILD_STATE")
                        .data("{\"heroes\":%d,\"openQuests\":%d,\"completedQuests\":%d}"
                                .formatted(guild.getMembers().size(),
                                        guild.getOpenQuestCount(),
                                        guild.getCompletedQuestCount())));
            } catch (IOException ignored) {}
        });

        return emitter;
    }

    public void broadcast(String eventName, String data) {
        List.copyOf(emitters).forEach(e -> {
            try {
                e.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException ex) {
                emitters.remove(e);
            }
        });
    }
}