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
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.empty());
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
        AccessCredential active = AccessCredential.createCloud("04A1B2C3", 12L, 99L, "Otro", null, null);
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(active));

        assertThrows(DuplicateRfidUidException.class, () -> service.createCredential(cmd));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void createCredentialRejectsASuspendedCardToo() {
        AccessCredential suspended = AccessCredential.createCloud("04A1B2C3", 12L, 99L, "Otro", null, null);
        suspended.changeStatus(CredentialStatus.SUSPENDED, Instant.now());
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(suspended));

        assertThrows(DuplicateRfidUidException.class, () -> service.createCredential(
                new CreateCredentialCommand("04A1B2C3", 12L, 15L, "Ana Perez", null, null)));
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void createCredentialReassignsARevokedCardToTheNewHolder() {
        AccessCredential revoked = AccessCredential.createCloud("04A1B2C3", 7L, 99L, "Antiguo", null, null);
        revoked.changeStatus(CredentialStatus.REVOKED, Instant.now());
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(revoked));
        when(credentialRepository.save(any(AccessCredential.class))).thenAnswer(i -> i.getArgument(0));

        var saved = service.createCredential(
                new CreateCredentialCommand("04 a1 b2 c3", 12L, 15L, "Ana Perez", null, null)).orElseThrow();

        assertSame(revoked, saved);
        assertEquals(CredentialStatus.ACTIVE, saved.getStatus());
        assertEquals(15L, saved.getUserId());
        assertEquals("Ana Perez", saved.getHolderName());
        assertEquals(12L, saved.getBuildingId());
        // Los dos edificios se resincronizan: el nuevo la habilita y el anterior la retira
        verify(edgeSyncNotifier).changedForBuilding(12L);
        verify(edgeSyncNotifier).changedForBuilding(7L);
        verify(eventPublisher).publishEvent(any(CredentialCreatedEvent.class));
    }

    @Test
    void createCredentialReassignsALostCard() {
        AccessCredential lost = AccessCredential.createCloud("04A1B2C3", 12L, 99L, "Antiguo", null, null);
        lost.changeStatus(CredentialStatus.LOST, Instant.now());
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(lost));
        when(credentialRepository.save(any(AccessCredential.class))).thenAnswer(i -> i.getArgument(0));

        var saved = service.createCredential(
                new CreateCredentialCommand("04A1B2C3", 12L, 15L, "Ana Perez", null, null)).orElseThrow();

        assertEquals(CredentialStatus.ACTIVE, saved.getStatus());
        verify(edgeSyncNotifier, times(1)).changedForBuilding(12L);
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
