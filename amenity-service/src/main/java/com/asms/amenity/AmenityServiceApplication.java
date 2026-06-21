package com.asms.amenity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableCaching
@EnableMongoAuditing
public class AmenityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AmenityServiceApplication.class, args);
    }
}
