package com.skillforge.hub;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "guild.github.owner=test-owner",
        "guild.github.repo=test-repo",
        "guild.github.token=",
        "guild.amqp.exchange=test-exchange",
        "guild.amqp.queue=test-queue",
        "spring.rabbitmq.host=localhost",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class HubApplicationTests {

    // Satisfaz ProblemPublisher sem conexão real ao broker
    @MockBean
    RabbitTemplate rabbitTemplate;

    @Test
    void contextLoads() {
    }
}