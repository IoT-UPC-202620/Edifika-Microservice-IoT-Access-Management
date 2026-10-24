package com.edifika.access.access.infrastructure.persistence.jpa.repositories;

import com.edifika.access.access.domain.model.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
