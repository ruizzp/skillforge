package com.skillforge.hub.amqp;

import java.util.List;

public record ProblemMessage(
        String questId,
        String problem,
        List<String> requiredSkills,
        int xpReward,
        String submittedBy
) {}
