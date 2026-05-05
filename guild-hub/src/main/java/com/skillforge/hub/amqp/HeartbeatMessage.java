package com.skillforge.hub.amqp;

import java.time.Instant;
import java.util.List;

public record HeartbeatMessage(
        String heroId,
        String heroName,
        List<String> skills,
        Instant timestamp
) {}
