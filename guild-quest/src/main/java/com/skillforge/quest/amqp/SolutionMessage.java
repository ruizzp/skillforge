package com.skillforge.quest.amqp;

public record SolutionMessage(
    String questId,
    String heroId,
    String heroName,
    String solution,
    double confidence,
    String model
) {}