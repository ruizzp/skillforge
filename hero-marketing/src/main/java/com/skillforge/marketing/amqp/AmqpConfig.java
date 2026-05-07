package com.skillforge.marketing.amqp;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    static final String QUEUE   = "hero-marketing.problems";
    static final String EXCHANGE = "skillforge";
    static final String KEY     = "problem.pitch-design";

    @Bean
    Queue pitchQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    TopicExchange skillforgeExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Binding pitchBinding(Queue pitchQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(pitchQueue).to(skillforgeExchange).with(KEY);
    }
}