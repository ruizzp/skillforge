package com.skillforge.quest.amqp;

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

    static final String QUEUE = "guild-quest.problems";
    static final String KEY   = "problem.quest-design";

    @Value("${guild.amqp.exchange:skillforge}")
    private String exchangeName;

    @Bean TopicExchange skillforgeExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean Queue questQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean Binding questBinding(Queue questQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(questQueue).to(skillforgeExchange).with(KEY);
    }

    @Bean Jackson2JsonMessageConverter messageConverter(ObjectMapper mapper) {
        var converter = new Jackson2JsonMessageConverter(mapper);
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        var t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        return t;
    }
}