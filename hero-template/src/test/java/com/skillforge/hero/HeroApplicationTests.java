package com.skillforge.hero;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "AMQP_URL=amqp://localhost",
        "guild.github.owner=test-owner",
        "guild.github.repo=test-repo",
        "guild.github.token=",
        "guild.amqp.exchange=test-exchange",
        "guild.amqp.queue=test-queue",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class HeroApplicationTests {

    // RabbitAutoConfiguration excluída — mocks satisfazem todas as dependências AMQP
    @MockBean
    ConnectionFactory connectionFactory;

    @MockBean
    RabbitTemplate rabbitTemplate;

    @Test
    void contextLoads() {
    }
}