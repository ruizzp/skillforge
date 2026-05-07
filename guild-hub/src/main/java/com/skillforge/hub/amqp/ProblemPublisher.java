package com.skillforge.hub.amqp;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProblemPublisher {

    private final RabbitTemplate rabbit;
    private final String exchange;

    public ProblemPublisher(RabbitTemplate rabbit,
                            @Value("${guild.amqp.exchange}") String exchange) {
        this.rabbit = rabbit;
        this.exchange = exchange;
    }

    /** Publica com routing key genérica (legado). */
    public void publish(ProblemMessage msg) {
        rabbit.convertAndSend(exchange, "problem", msg);
    }

    /** Publica com routing key específica da skill — ex: problem.quest-design. */
    public void publish(ProblemMessage msg, String routingKey) {
        rabbit.convertAndSend(exchange, routingKey, msg);
    }
}