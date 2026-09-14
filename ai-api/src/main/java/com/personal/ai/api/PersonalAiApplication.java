package com.personal.ai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.personal.ai")
public class PersonalAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(PersonalAiApplication.class, args);
    }
}
