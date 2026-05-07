package com.skillforge.hub.amqp;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    public static final String SOLUTIONS_QUEUE   = "skillforge.solutions";
    public static final String HEARTBEATS_QUEUE  = "skillforge.heartbeats";

    @Value("${guild.amqp.exchange}")
    private String exchangeName;

    @Value("${guild.amqp.queue}")
    private String problemsQueue;

    @Bean
    public TopicExchange skillforgeExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    // Hub listens on this queue for solutions posted by heroes
    @Bean
    public Queue solutionsQueue() {
        return new Queue(SOLUTIONS_QUEUE, true);
    }

    @Bean
    public Binding solutionsBinding(Queue solutionsQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(solutionsQueue).to(skillforgeExchange).with("solution.#");
    }

    // Problems queue — hub publishes here, heroes consume
    @Bean
    public Queue problemsQueue() {
        return new Queue(problemsQueue, true);
    }

    @Bean
    public Binding problemsBinding(Queue problemsQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(problemsQueue).to(skillforgeExchange).with("problem");
    }

    @Bean
    public Queue heartbeatsQueue() {
        return new Queue(HEARTBEATS_QUEUE, true);
    }

    @Bean
    public Binding heartbeatsBinding(Queue heartbeatsQueue, TopicExchange skillforgeExchange) {
        return BindingBuilder.bind(heartbeatsQueue).to(skillforgeExchange).with("heartbeat");
    }

    @Bean
    public MessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        var template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }
}