package com.skillforge.hero.domain;

import java.util.List;

public record GuildMember(
        String heroId,
        String heroName,
        String heroClass,
        List<String> skills,
        List<String> validatedSkills,
        int level,
        int xp,
        String specialty,
        String githubLogin,
        String avatarUrl
) {}