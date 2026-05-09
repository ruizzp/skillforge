package com.skillforge.hub.amqp;

public record ReviewResultMessage(
    String questId,
    String heroId,
    boolean approved,
    String feedback,         // actionable feedback if not approved
    double reviewScore,      // 0.0–1.0 reviewer confidence
    int revisionCount,
    String reviewerModel,
    String reviewerId        // heroId of the reviewing node (for XP credit)
) {}