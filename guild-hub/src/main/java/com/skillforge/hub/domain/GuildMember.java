package com.skillforge.hub.domain;

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
        String avatarUrl,
        int issueNumber
) {
    public SkillValidation validationOf(String skill) {
        if (validatedSkills.contains(skill)) return SkillValidation.PROVEN;
        if (skills.contains(skill))          return SkillValidation.DECLARED;
        return SkillValidation.UNKNOWN;
    }
}