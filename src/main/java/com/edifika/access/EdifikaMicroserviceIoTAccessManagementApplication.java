package com.edifika.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio IoT Access Management de Edifika.
 */
@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class EdifikaMicroserviceIoTAccessManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdifikaMicroserviceIoTAccessManagementApplication.class, args);
    }
}
