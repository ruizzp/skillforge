package com.skillforge.hub.service;

import com.skillforge.hub.domain.Quest;
import com.skillforge.hub.domain.QuestRarity;
import com.skillforge.hub.github.GitHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class QuestBoardService {

    private static final Logger log = LoggerFactory.getLogger(QuestBoardService.class);

    private final GitHubClient github;
    private final AtomicReference<List<Quest>> quests = new AtomicReference<>(List.of());

    public QuestBoardService(GitHubClient github) {
        this.github = github;
        refresh();
    }

    public List<Quest> getQuests() {
        return quests.get();
    }

    public List<Quest> getOpenQuests() {
        return quests.get().stream().filter(Quest::isAvailable).toList();
    }

    public List<Quest> getCompletedQuests() {
        return quests.get().stream().filter(Quest::isCompleted).toList();
    }

    public List<Quest> getQuestsByRarity(QuestRarity rarity) {
        return quests.get().stream().filter(q -> q.rarity() == rarity).toList();
    }

    public Map<QuestRarity, Long> getCountByRarity() {
        return quests.get().stream()
                .collect(Collectors.groupingBy(Quest::rarity, Collectors.counting()));
    }

    public long getOpenCount() {
        return quests.get().stream().filter(Quest::isAvailable).count();
    }

    public long getCompletedCount() {
        return quests.get().stream().filter(Quest::isCompleted).count();
    }

    /**
     * Creates a quest as a GitHub Issue and returns the issue number.
     * Requires GITHUB_TOKEN to be set.
     */
    public int createQuest(String title, String body, QuestRarity rarity,
                           int xpReward, List<String> requiredSkills) throws Exception {
        int number = github.createQuestIssue(title, body, rarity, xpReward, requiredSkills);
        refresh();
        log.info("Quest criada: [{}] {} — issue #{}", rarity, title, number);
        return number;
    }

    @Scheduled(fixedDelay = 300_000)
    public void refresh() {
        List<Quest> fetched = github.fetchQuests();
        quests.set(fetched);
        log.debug("Quest board refreshed — {} quests", fetched.size());
    }
}