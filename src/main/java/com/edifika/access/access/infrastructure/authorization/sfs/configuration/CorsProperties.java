package com.edifika.access.access.infrastructure.authorization.sfs.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Configuración CORS (prefijo {@code cors}).
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @DefaultValue({"http://localhost:4200", "http://localhost:5173", "http://localhost:3000"})
        List<String> allowedOrigins
) {
}
