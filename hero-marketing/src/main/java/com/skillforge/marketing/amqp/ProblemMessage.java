package com.skillforge.marketing.amqp;

import java.util.List;

public record ProblemMessage(
        String questId,
        String problem,
        List<String> requiredSkills,
        int xpReward,
        String submittedBy,
        String questUrl,
        String questBody
) {}