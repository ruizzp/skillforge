package com.skillforge.quest.domain;

public record QuestRequest(
    String domain,       // ex: "medical", "financial"
    String questType,    // ex: "triagem", "validação", "parser"
    String level,        // COMMON | RARE | EPIC | LEGENDARY
    String context       // descrição livre do que a quest deve ensinar
) {}