package com.skillforge.marketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HeroMarketingApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeroMarketingApplication.class, args);
    }
}
