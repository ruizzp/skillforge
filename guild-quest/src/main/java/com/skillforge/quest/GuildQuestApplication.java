package com.skillforge.quest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GuildQuestApplication {
    public static void main(String[] args) {
        SpringApplication.run(GuildQuestApplication.class, args);
    }
}