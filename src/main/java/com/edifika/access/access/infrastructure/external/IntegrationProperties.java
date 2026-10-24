package com.edifika.access.access.infrastructure.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {
    private String residentialUrl = "http://localhost:8081";
    private String paymentUrl = "http://localhost:8082";
    private boolean debtCheckEnabled = true;
    private int debtCheckIntervalMinutes = 5;
    private String timezone = "America/Lima";
    private int timeoutSeconds = 5;
    private int deviceCommandTtlSeconds = 30;
    private int reservationMaxHours = 12;
}
