package com.edifika.access.access.domain.model.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Entidad para registro idempotente de eventos procesados (contratos §3.6).
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64, nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent(String eventId, Instant processedAt) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId es obligatorio");
        }
        this.eventId = eventId;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
    }
}
