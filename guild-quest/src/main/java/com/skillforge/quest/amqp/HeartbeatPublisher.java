package com.skillforge.quest.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class HeartbeatPublisher {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatPublisher.class);

    private final RabbitTemplate rabbit;
    private final String exchange;
    private final String heroId;
    private final String heroName;
    private final List<String> skills;

    public HeartbeatPublisher(
            RabbitTemplate rabbit,
            @Value("${guild.amqp.exchange:skillforge}") String exchange,
            @Value("${skillforge.hero.id:guild-quest}")   String heroId,
            @Value("${skillforge.hero.name:Guild Quest}") String heroName,
            @Value("${skillforge.hero.skills:quest-design,domain-modeling,fixture-generation}") String skillsCsv) {
        this.rabbit   = rabbit;
        this.exchange = exchange;
        this.heroId   = heroId;
        this.heroName = heroName;
        this.skills   = List.of(skillsCsv.split(","));
    }

    @Scheduled(fixedDelayString  = "${guild.amqp.heartbeat-interval-ms:60000}",
               initialDelayString = "${guild.amqp.heartbeat-initial-delay-ms:5000}")
    public void publish() {
        var msg = Map.of(
            "heroId",    heroId,
            "heroName",  heroName,
            "skills",    skills,
            "timestamp", Instant.now().toString()
        );
        rabbit.convertAndSend(exchange, "heartbeat", msg);
        log.debug("Heartbeat enviado — heroId: {}", heroId);
    }
}