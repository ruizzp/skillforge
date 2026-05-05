package com.skillforge.hub.domain;

public record LeaderboardEntry(
        int rank,
        GuildMember member,
        HeroLevel heroLevel
) {}