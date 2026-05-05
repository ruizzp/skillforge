package com.skillforge.hub.amqp;

import java.time.Instant;

public record SolutionMessage(
        String questId,
        String heroId,
        String heroName,
        String solution,
        double confidence,
        String model,
        Instant solvedAt
) {}