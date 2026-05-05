package com.skillforge.hub.domain;

public enum QuestRarity {
    COMMON   (1, 50,   200),
    RARE     (3, 200,  500),
    EPIC     (5, 500,  1000),
    LEGENDARY(8, 1000, Integer.MAX_VALUE);

    public final int minLevel;
    public final int minXp;
    public final int maxXp;

    QuestRarity(int minLevel, int minXp, int maxXp) {
        this.minLevel = minLevel;
        this.minXp = minXp;
        this.maxXp = maxXp;
    }

    public static QuestRarity fromLabel(String label) {
        try {
            return valueOf(label.toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}