package com.skillforge.quest.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    static final String QUEST_QUEUE = "guild-quest.problems";

    @Value("${guild.amqp.exchange:skillforge}")
    private String exchangeName;

    @Bean TopicExchange exchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean Queue questQueue() {
        return QueueBuilder.durable(QUEST_QUEUE).build();
    }

    @Bean Binding questBinding(Queue questQueue, TopicExchange exchange) {
        return BindingBuilder.bind(questQueue).to(exchange).with("problem.quest-design");
    }

    @Bean Jackson2JsonMessageConverter messageConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, Jackson2JsonMessageConverter conv) {
        var t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        return t;
    }
}