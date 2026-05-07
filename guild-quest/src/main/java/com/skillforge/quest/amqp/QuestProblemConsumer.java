package com.skillforge.quest.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.quest.domain.QuestRequest;
import com.skillforge.quest.service.QuestGuardianService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class QuestProblemConsumer {

    private static final Logger log = LoggerFactory.getLogger(QuestProblemConsumer.class);

    private final QuestGuardianService guardian;
    private final RabbitTemplate amqp;
    private final ObjectMapper mapper;

    public QuestProblemConsumer(QuestGuardianService guardian, RabbitTemplate amqp, ObjectMapper mapper) {
        this.guardian = guardian;
        this.amqp = amqp;
        this.mapper = mapper;
    }

    @RabbitListener(queues = AmqpConfig.QUEST_QUEUE)
    public void onProblem(ProblemMessage msg) {
        log.info("Quest request recebido — de: {} | questId: {}", msg.heroId(), msg.questId());

        try {
            var request = mapper.readValue(msg.problem(), QuestRequest.class);
            var draft = guardian.createQuest(request);

            var solution = new SolutionMessage(
                msg.questId(),
                "guild-quest",
                "Guild Quest",
                draft.markdown(),
                draft.confidence(),
                draft.model()
            );

            amqp.convertAndSend(AmqpConfig.EXCHANGE, "solution", solution);
            log.info("Quest draft publicado — domínio: {} | confiança: {}%",
                request.domain(), (int)(draft.confidence() * 100));

        } catch (Exception e) {
            log.error("Falha ao processar quest request de {}: {}", msg.heroId(), e.getMessage());

            var failure = new SolutionMessage(
                msg.questId(), "guild-quest", "Guild Quest",
                "Falha ao gerar quest: " + e.getMessage(), 0.0, "error"
            );
            amqp.convertAndSend(AmqpConfig.EXCHANGE, "solution", failure);
        }
    }
}