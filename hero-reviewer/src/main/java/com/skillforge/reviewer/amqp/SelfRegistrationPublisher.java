package com.skillforge.reviewer.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SelfRegistrationPublisher {

    private static final Logger log = LoggerFactory.getLogger(SelfRegistrationPublisher.class);

    private final RabbitTemplate rabbit;
    private final String exchange;
    private final String heroId;
    private final String heroName;
    private final String heroClass;
    private final List<String> skills;
    private final String specialty;
    private final String endpoint;
    private final String model;

    public SelfRegistrationPublisher(
            RabbitTemplate rabbit,
            @Value("${guild.amqp.exchange:skillforge}")      String exchange,
            @Value("${skillforge.hero.id:hero-reviewer}")    String heroId,
            @Value("${skillforge.hero.name:Hero Reviewer}")  String heroName,
            @Value("${skillforge.hero.class:Quality Guardian}") String heroClass,
            @Value("${skillforge.hero.skills:quest-review,solution-evaluation,dod-validation,feedback-generation}") String skillsCsv,
            @Value("${skillforge.hero.specialty:Avalia soluções contra o DoD da quest e gera feedback acionável}") String specialty,
            @Value("${skillforge.hero.endpoint:http://localhost:8093}") String endpoint,
            @Value("${ollama.model:llama3.2}")                String model) {
        this.rabbit    = rabbit;
        this.exchange  = exchange;
        this.heroId    = heroId;
        this.heroName  = heroName;
        this.heroClass = heroClass;
        this.skills    = List.of(skillsCsv.split(","));
        this.specialty = specialty;
        this.endpoint  = endpoint;
        this.model     = model;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        try {
            var msg = new RegistrationMessage(heroId, heroName, heroClass, skills, specialty, endpoint, model);
            rabbit.convertAndSend(exchange, "hero.register", msg);
            log.info("Auto-registro enviado — heroId: {} | skills: {}", heroId, skills);
        } catch (Exception e) {
            log.warn("Falha ao enviar auto-registro: {}", e.getMessage());
        }
    }

    record RegistrationMessage(
        String heroId,
        String heroName,
        String heroClass,
        List<String> skills,
        String specialty,
        String endpoint,
        String model
    ) {}
}
