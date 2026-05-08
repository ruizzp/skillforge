package com.skillforge.hero.amqp;

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

    // Rename to match your hero: "{hero-id}.problems"
    static final String QUEUE = "hero-template.problems";
    // Adjust routing key to match the skills your hero resolves
    static final String KEY   = "problem.#";

    @Value("${guild.amqp.exchange:skillforge}")
    private String exchangeName;

    @Bean
    TopicExchange skillforgeExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue problemQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding problemBinding(Queue problemQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(problemQueue).to(skillforgeExchange).with(KEY);
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter(ObjectMapper mapper) {
        var converter = new Jackson2JsonMessageConverter(mapper);
        // REQUIRED: use the @RabbitListener parameter type, not the __TypeId__ header sent by hub.
        // Without this, deserialization fails with ClassNotFoundException on the hub's package.
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