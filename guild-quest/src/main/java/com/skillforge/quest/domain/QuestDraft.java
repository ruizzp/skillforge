package com.skillforge.quest.domain;

import java.util.List;

public record QuestDraft(
    String markdown,
    String title,
    String rarity,
    int xpReward,
    List<String> requiredSkills,
    double confidence,
    String model
) {}