package com.skillforge.marketing.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    static final String QUEUE    = "hero-marketing.problems";
    static final String EXCHANGE = "skillforge";
    static final String KEY      = "problem.#";  // recebe qualquer problema roteado para skills deste hero

    @Bean
    Queue pitchQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    TopicExchange skillforgeExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Binding pitchBinding(Queue pitchQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(pitchQueue).to(skillforgeExchange).with(KEY);
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter(ObjectMapper mapper) {
        var converter = new Jackson2JsonMessageConverter(mapper);
        // use o tipo do parâmetro do @RabbitListener, não o __TypeId__ header enviado pelo hub
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