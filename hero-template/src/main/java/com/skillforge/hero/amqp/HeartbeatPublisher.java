package com.skillforge.hero.amqp;

import com.skillforge.hero.service.GuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HeartbeatPublisher {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatPublisher.class);

    private final GuildService guildService;
    private final RabbitTemplate rabbit;
    private final String exchange;

    public HeartbeatPublisher(GuildService guildService,
                              RabbitTemplate rabbit,
                              @Value("${guild.amqp.exchange}") String exchange) {
        this.guildService = guildService;
        this.rabbit = rabbit;
        this.exchange = exchange;
    }

    @Scheduled(fixedDelayString = "${guild.amqp.heartbeat-interval-ms:60000}",
               initialDelayString = "${guild.amqp.heartbeat-interval-ms:60000}")
    public void publish() {
        var manifest = guildService.getManifest();
        var msg = new HeartbeatMessage(
                manifest.heroId(),
                manifest.heroName(),
                manifest.skills(),
                Instant.now()
        );
        rabbit.convertAndSend(exchange, "heartbeat", msg);
        log.debug("Heartbeat enviado — herói: {}", manifest.heroId());
    }
}
