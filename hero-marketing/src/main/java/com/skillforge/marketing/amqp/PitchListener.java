package com.skillforge.marketing.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillforge.marketing.domain.ProjectBrief;
import com.skillforge.marketing.service.PitchGeneratorService;
import com.skillforge.marketing.service.PitchValidatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PitchListener {

    private static final Logger log = LoggerFactory.getLogger(PitchListener.class);
    private static final String SOLUTION_KEY = "solution.pitch-design";

    private final PitchGeneratorService generator;
    private final PitchValidatorService validator;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    public PitchListener(PitchGeneratorService generator,
                         PitchValidatorService validator,
                         RabbitTemplate rabbit,
                         ObjectMapper mapper) {
        this.generator = generator;
        this.validator = validator;
        this.rabbit    = rabbit;
        this.mapper    = mapper;
    }

    @RabbitListener(queues = AmqpConfig.QUEUE)
    public void onProblem(String message) {
        try {
            var brief = mapper.readValue(message, ProjectBrief.class);
            log.info("Problema recebido via AMQP para projeto '{}'", brief.projectName());

            Map<String, String> docs = generator.generate(brief);
            var draft = validator.validate(docs.get("guildPitch"), docs.get("investorOnePager"));

            var solution = mapper.writeValueAsString(Map.of(
                "projectId",   brief.projectId(),
                "valid",       draft.valid(),
                "confidence",  draft.confidence(),
                "guildPitch",  draft.guildPitch(),
                "investorOnePager", draft.investorOnePager(),
                "veredicto",   draft.veredicto()
            ));

            rabbit.convertAndSend(AmqpConfig.EXCHANGE, SOLUTION_KEY, solution);
            log.info("Solução publicada para '{}' — confidence: {}", brief.projectId(), draft.confidence());

        } catch (Exception e) {
            log.error("Falha ao processar problema de pitch: {}", e.getMessage());
        }
    }
}