package com.skillforge.marketing.amqp;

import java.time.Instant;
import java.util.List;

public record SolutionMessage(
        String questId,
        String heroId,
        String heroName,
        String solution,       // resumo textual da solução
        double confidence,
        String model,
        Instant solvedAt
) {}