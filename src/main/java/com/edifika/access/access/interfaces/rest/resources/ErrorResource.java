package com.edifika.access.access.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * Resource de salida uniforme para todas las respuestas de error de la API.
 */
@Schema(description = "Respuesta de error")
public record ErrorResource(
        @Schema(example = "409") int status,
        @Schema(example = "Conflict") String error,
        @Schema(example = "La puerta ya está reservada en ese horario") String message,
        Instant timestamp
) {
    public static ErrorResource of(HttpStatus status, String message) {
        return new ErrorResource(status.value(), status.getReasonPhrase(), message, Instant.now());
    }
}
