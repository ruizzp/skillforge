package com.skillforge.hub.amqp;

import java.util.List;

public record HeroSelfRegistrationMessage(
        String heroId,
        String heroName,
        String heroClass,
        List<String> skills,
        String specialty,
        String endpoint,
        String model
) {}