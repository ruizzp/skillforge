package com.skillforge.reviewer.amqp;

public record ReviewMessage(
    String questId,
    int questIssueNumber,
    String questTitle,
    String questBody,      // full body including DoD section
    String solution,
    String heroId,
    String heroName,
    double confidence,
    String model,
    int revisionCount
) {}