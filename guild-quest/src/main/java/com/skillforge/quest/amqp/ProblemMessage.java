package com.skillforge.quest.amqp;

import java.util.List;

public record ProblemMessage(
    String questId,
    String heroId,
    String problem,
    List<String> requiredSkills
) {}