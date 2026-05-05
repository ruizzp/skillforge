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
        int number
) {
    public boolean isAvailable() { return "open".equals(status); }
    public boolean isCompleted() { return "closed".equals(status); }
}