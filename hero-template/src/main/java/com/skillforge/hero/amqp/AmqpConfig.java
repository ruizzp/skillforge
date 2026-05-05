package com.skillforge.hero.amqp;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    @Value("${guild.amqp.exchange}")
    private String exchangeName;

    @Value("${guild.amqp.queue}")
    private String queueName;

    @Bean
    public TopicExchange skillforgeExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue problemQueue() {
        return new Queue(queueName, true);
    }

    @Bean
    public MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }
}