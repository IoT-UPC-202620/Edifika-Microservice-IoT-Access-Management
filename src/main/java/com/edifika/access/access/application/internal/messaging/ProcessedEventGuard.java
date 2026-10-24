package com.edifika.access.access.application.internal.messaging;

import com.edifika.access.access.domain.model.entities.ProcessedEvent;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ProcessedEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Guarda de idempotencia: inserta el eventId y retorna false si ya existía.
 */
@Slf4j
@Component
public class ProcessedEventGuard {

    private final ProcessedEventRepository repository;

    public ProcessedEventGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean firstTime(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        if (repository.existsById(eventId)) {
            log.debug("Evento ya procesado previamente: {}", eventId);
            return false;
        }
        try {
            repository.saveAndFlush(new ProcessedEvent(eventId, Instant.now()));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Conflicto de unicidad en eventId {}: {}", eventId, e.getMessage());
            return false;
        }
    }
}
