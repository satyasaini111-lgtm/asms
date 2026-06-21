package com.asms.visitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableMongoAuditing
@EnableKafka
@EnableScheduling
public class VisitorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VisitorServiceApplication.class, args);
    }
}
