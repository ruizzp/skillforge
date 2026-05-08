package com.skillforge.hub.domain;

import java.util.List;

public record Quest(
        String id,
        String title,
        String body,
        QuestRarity rarity,
        String status,
        List<String> requiredSkills,
        int xpReward,
        String assignee,
        String url,
        int number,
        List<String> solvers,   // heroIds de todos os labels "solved-by:{heroId}"
        String assignedTo,      // heroId do label "assigned-to:{heroId}"
        int revisionCount       // parsed from label "revision-count:{n}", default 0
) {
    public boolean isAvailable() { return "open".equals(status); }
    public boolean isCompleted() { return "closed".equals(status); }
}