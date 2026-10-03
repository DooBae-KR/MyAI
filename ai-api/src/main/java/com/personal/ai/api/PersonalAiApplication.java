package com.personal.ai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.personal.ai")
@ConfigurationPropertiesScan
@EnableScheduling
public class PersonalAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersonalAiApplication.class, args);
    }
}
