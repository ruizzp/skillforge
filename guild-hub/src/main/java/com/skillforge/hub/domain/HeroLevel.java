package com.skillforge.hub.domain;

public enum HeroLevel {
    APPRENTICE(1,  0,     "Quests COMMON"),
    JOURNEYMAN(3,  1000,  "Quests RARE + visão de arquitetura"),
    EXPERT    (5,  3000,  "Quests EPIC + métricas da guilda"),
    MASTER    (8,  8000,  "Quests LEGENDARY + mentoria"),
    ARCHMAGE  (10, 20000, "Define skills + aprova quests");

    public final int level;
    public final int xpRequired;
    public final String perks;

    HeroLevel(int level, int xpRequired, String perks) {
        this.level = level;
        this.xpRequired = xpRequired;
        this.perks = perks;
    }

    public static HeroLevel fromXp(int xp) {
        HeroLevel result = APPRENTICE;
        for (HeroLevel hl : values()) {
            if (xp >= hl.xpRequired) result = hl;
        }
        return result;
    }

    public static HeroLevel fromLevel(int level) {
        HeroLevel result = APPRENTICE;
        for (HeroLevel hl : values()) {
            if (level >= hl.level) result = hl;
        }
        return result;
    }

    public boolean canSee(QuestRarity rarity) {
        return level >= rarity.minLevel;
    }
}