package com.edifika.access.domain;

import com.edifika.access.access.application.internal.commandservices.CredentialCommandServiceImpl;
import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.DuplicateRfidUidException;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.commands.CreateCredentialCommand;
import com.edifika.access.access.domain.model.commands.UpsertEdgeCredentialCommand;
import com.edifika.access.access.domain.model.events.CredentialCreatedEvent;
import com.edifika.access.access.domain.model.valueobjects.CredentialOrigin;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialCommandServiceImplTest {

    @Mock
    private AccessCredentialRepository credentialRepository;

    @Mock
    private EdgeSyncNotifier edgeSyncNotifier;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CredentialCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CredentialCommandServiceImpl(credentialRepository, edgeSyncNotifier, eventPublisher);
    }

    @Test
    void createCredentialPublishesEventAndNotifiesEdge() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "04A1B2C3", 12L, 15L, "Ana Perez", null, null
        );
        when(credentialRepository.existsByRfidUid("04A1B2C3")).thenReturn(false);
        when(credentialRepository.save(any(AccessCredential.class))).thenAnswer(i -> i.getArgument(0));

        var opt = service.createCredential(cmd);
        assertTrue(opt.isPresent());
        assertEquals("04A1B2C3", opt.get().getRfidUid());
        verify(edgeSyncNotifier).changedForBuilding(12L);
        verify(eventPublisher).publishEvent(any(CredentialCreatedEvent.class));
    }

    @Test
    void createCredentialRejectsDuplicate() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "04A1B2C3", 12L, 15L, "Ana Perez", null, null
        );
        when(credentialRepository.existsByRfidUid("04A1B2C3")).thenReturn(true);

        assertThrows(DuplicateRfidUidException.class, () -> service.createCredential(cmd));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void upsertFromEdgeCreatesEdgeOriginCredentialWithoutUser() {
        UpsertEdgeCredentialCommand cmd = new UpsertEdgeCredentialCommand(
                "04A1B2C3", "guardia-1", "ACTIVE", Instant.now(), 12L
        );
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.empty());

        service.upsertFromEdge(cmd);

        ArgumentCaptor<AccessCredential> captor = ArgumentCaptor.forClass(AccessCredential.class);
        verify(credentialRepository).save(captor.capture());
        AccessCredential saved = captor.getValue();
        assertEquals(CredentialOrigin.EDGE, saved.getOrigin());
        assertNull(saved.getUserId());
        assertEquals("guardia-1", saved.getEdgeResidentRef());
        assertEquals(CredentialStatus.ACTIVE, saved.getStatus());
    }

    @Test
    void upsertFromEdgeLosesAgainstNewerCloudChange() {
        AccessCredential credential = AccessCredential.createCloud(
                "04A1B2C3", 12L, 15L, "Ana Perez", null, null
        );
        credential.setAuditDates(new Date(System.currentTimeMillis() - 1000), new Date());
        Instant eventTime = Instant.now().minusSeconds(60);
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(credential));

        UpsertEdgeCredentialCommand cmd = new UpsertEdgeCredentialCommand(
                "04A1B2C3", "guardia-1", "REVOKED", eventTime, 12L
        );

        service.upsertFromEdge(cmd);

        // Status should remain unchanged (ACTIVE)
        assertEquals(CredentialStatus.ACTIVE, credential.getStatus());
    }
}
