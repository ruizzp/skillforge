package com.skillforge.hub.amqp;

import java.util.List;

public record ProblemMessage(
        String questId,
        String problem,
        List<String> requiredSkills,
        int xpReward,
        String submittedBy,
        String questUrl,    // URL da issue no GitHub — para o hero postar o resultado
        String questBody    // corpo completo da quest — contexto para geração
) {}
