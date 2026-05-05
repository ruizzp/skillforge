package com.skillforge.hub.amqp;

import com.skillforge.hub.service.HeroPresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class HeartbeatConsumer {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatConsumer.class);

    private final HeroPresenceService presence;

    public HeartbeatConsumer(HeroPresenceService presence) {
        this.presence = presence;
    }

    @RabbitListener(queues = AmqpConfig.HEARTBEATS_QUEUE)
    public void onHeartbeat(HeartbeatMessage msg) {
        log.debug("Heartbeat recebido — herói: {} em {}", msg.heroId(), msg.timestamp());
        presence.recordHeartbeat(msg.heroId(), msg.heroName());
    }
}
