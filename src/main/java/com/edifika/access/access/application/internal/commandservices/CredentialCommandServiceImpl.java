package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.CredentialNotFoundException;
import com.edifika.access.access.domain.exceptions.DuplicateRfidUidException;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.commands.ChangeCredentialStatusCommand;
import com.edifika.access.access.domain.model.commands.CreateCredentialCommand;
import com.edifika.access.access.domain.model.commands.UpsertEdgeCredentialCommand;
import com.edifika.access.access.domain.model.events.CredentialCreatedEvent;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import com.edifika.access.access.domain.model.valueobjects.RfidUid;
import com.edifika.access.access.domain.services.CredentialCommandService;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class CredentialCommandServiceImpl implements CredentialCommandService {

    private final AccessCredentialRepository credentialRepository;
    private final EdgeSyncNotifier edgeSyncNotifier;
    private final ApplicationEventPublisher eventPublisher;

    public CredentialCommandServiceImpl(
            AccessCredentialRepository credentialRepository,
            EdgeSyncNotifier edgeSyncNotifier,
            ApplicationEventPublisher eventPublisher) {
        this.credentialRepository = credentialRepository;
        this.edgeSyncNotifier = edgeSyncNotifier;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<AccessCredential> createCredential(CreateCredentialCommand command) {
        String normalizedUid = new RfidUid(command.rfidUid()).value();
        if (credentialRepository.existsByRfidUid(normalizedUid)) {
            throw new DuplicateRfidUidException(normalizedUid);
        }

        AccessCredential credential = AccessCredential.createCloud(
                normalizedUid,
                command.buildingId(),
                command.userId(),
                command.holderName(),
                command.validFrom(),
                command.validUntil()
        );

        AccessCredential saved = credentialRepository.save(credential);
        edgeSyncNotifier.changedForBuilding(command.buildingId());

        eventPublisher.publishEvent(new CredentialCreatedEvent(
                command.userId(),
                saved.getId(),
                saved.getRfidUid(),
                command.buildingId()
        ));

        return Optional.of(saved);
    }

    @Override
    public Optional<AccessCredential> changeStatus(ChangeCredentialStatusCommand command) {
        AccessCredential credential = credentialRepository.findById(command.credentialId())
                .orElseThrow(() -> new CredentialNotFoundException(command.credentialId()));

        credential.changeStatus(command.status(), Instant.now());
        AccessCredential saved = credentialRepository.save(credential);

        edgeSyncNotifier.changedForBuilding(saved.getBuildingId());
        return Optional.of(saved);
    }

    @Override
    public void upsertFromEdge(UpsertEdgeCredentialCommand command) {
        String normalizedUid = new RfidUid(command.uid()).value();
        Optional<AccessCredential> existing = credentialRepository.findByRfidUid(normalizedUid);

        if (existing.isPresent()) {
            AccessCredential credential = existing.get();
            Instant lastUpdated = credential.getUpdatedAt() != null
                    ? credential.getUpdatedAt().toInstant()
                    : (credential.getCreatedAt() != null ? credential.getCreatedAt().toInstant() : Instant.EPOCH);

            if (lastUpdated.isAfter(command.occurredAt())) {
                log.info("Ignorando actualización de credencial {} desde edge: cambio en nube más reciente ({}) que evento ({})",
                        normalizedUid, lastUpdated, command.occurredAt());
                return;
            }

            credential.updateStatusFromEdge(CredentialStatus.valueOf(command.status()));
            credentialRepository.save(credential);
        } else {
            AccessCredential credential = AccessCredential.createFromEdge(
                    normalizedUid,
                    command.residentRef(),
                    CredentialStatus.valueOf(command.status()),
                    command.buildingId()
            );
            credentialRepository.save(credential);
        }
    }
}
