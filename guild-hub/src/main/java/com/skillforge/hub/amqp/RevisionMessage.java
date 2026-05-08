package com.skillforge.hub.amqp;

import java.util.List;

public record RevisionMessage(
        String questId,
        int issueNumber,
        String heroId,
        int revisionNumber,
        List<String> requiredSkills
) {}