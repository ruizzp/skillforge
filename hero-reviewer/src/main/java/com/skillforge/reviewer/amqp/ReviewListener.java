package com.skillforge.reviewer.amqp;

import com.skillforge.reviewer.service.QuestReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewListener {

    private static final Logger log = LoggerFactory.getLogger(ReviewListener.class);

    private static final String RESULT_ROUTING_KEY = "review-result.quest";

    private final QuestReviewService reviewService;
    private final RabbitTemplate rabbit;

    @Value("${guild.amqp.exchange:skillforge}")
    private String exchange;

    public ReviewListener(QuestReviewService reviewService, RabbitTemplate rabbit) {
        this.reviewService = reviewService;
        this.rabbit        = rabbit;
    }

    @RabbitListener(queues = AmqpConfig.QUEUE)
    public void onReview(ReviewMessage msg) {
        log.info("Revisão recebida — quest: {} | herói: {} | tentativa: {}",
                msg.questId(), msg.heroId(), msg.revisionCount());

        ReviewResultMessage result = reviewService.review(msg);

        rabbit.convertAndSend(exchange, RESULT_ROUTING_KEY, result);
        log.info("Resultado de revisão publicado — quest: {} | aprovado: {} | score: {}",
                result.questId(), result.approved(), result.reviewScore());
    }
}