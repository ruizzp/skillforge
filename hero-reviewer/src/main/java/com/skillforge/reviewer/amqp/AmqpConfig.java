package com.skillforge.reviewer.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    public static final String QUEUE = "hero-reviewer.reviews";
    static final String KEY          = "review.#";

    @Value("${guild.amqp.exchange:skillforge}")
    private String exchangeName;

    @Bean
    Queue reviewsQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    TopicExchange skillforgeExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Binding reviewsBinding(Queue reviewsQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(reviewsQueue).to(skillforgeExchange).with(KEY);
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter(ObjectMapper mapper) {
        var converter = new Jackson2JsonMessageConverter(mapper);
        // use the parameter type of @RabbitListener, not the __TypeId__ header sent by hub
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        var t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        return t;
    }
}