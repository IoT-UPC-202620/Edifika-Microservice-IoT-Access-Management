package com.edifika.access.access.application.internal.debts;

import com.edifika.access.access.application.internal.outboundservices.acl.ExternalDebtService;
import com.edifika.access.access.application.internal.outboundservices.acl.ExternalResidentDirectory;
import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.ExternalServiceUnavailableException;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.entities.ResidentStatus;
import com.edifika.access.access.domain.model.valueobjects.CredentialStatus;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.ResidentStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DebtCheckServiceTest {

    private ExternalResidentDirectory residentDirectory;
    private ExternalDebtService debtService;
    private ResidentStatusRepository residentStatusRepository;
    private AccessCredentialRepository credentialRepository;
    private EdgeGatewayRepository gatewayRepository;
    private EdgeSyncNotifier edgeSyncNotifier;
    private IntegrationProperties properties;
    private DebtCheckService debtCheckService;

    @BeforeEach
    void setUp() {
        residentDirectory = mock(ExternalResidentDirectory.class);
        debtService = mock(ExternalDebtService.class);
        residentStatusRepository = mock(ResidentStatusRepository.class);
        credentialRepository = mock(AccessCredentialRepository.class);
        gatewayRepository = mock(EdgeGatewayRepository.class);
        edgeSyncNotifier = mock(EdgeSyncNotifier.class);

        properties = new IntegrationProperties();
        properties.setDebtCheckEnabled(true);
        properties.setTimezone("America/Lima");

        debtCheckService = new DebtCheckService(
                residentDirectory,
                debtService,
                residentStatusRepository,
                credentialRepository,
                gatewayRepository,
                edgeSyncNotifier,
                properties
        );
    }

    @Test
    void overdueDebtMarksDelinquentAndSuspendsCards() {
        Long buildingId = 12L;
        Long userId = 15L;
        Long unitId = 101L;

        when(residentDirectory.residentsOfBuilding(buildingId))
                .thenReturn(List.of(new ExternalResidentDirectory.ResidentUnit(userId, unitId, true)));

        when(debtService.debtsOfUnit(unitId))
                .thenReturn(List.of(new ExternalDebtService.UnitDebt(1L, LocalDate.now().minusDays(5), "OVERDUE")));

        AccessCredential card = AccessCredential.createCloud("04A1B2C3", buildingId, userId, "Ana", null, null);
        when(credentialRepository.findAllByUserId(userId)).thenReturn(List.of(card));

        debtCheckService.refreshBuilding(buildingId);

        assertTrue(card.isSuspendedForDebt());
        assertEquals(CredentialStatus.SUSPENDED, card.getStatus());
        verify(credentialRepository).save(card);
        verify(edgeSyncNotifier).changedForBuilding(buildingId);
    }

    @Test
    void pendingPastDueCountsAsOverdue() {
        Long buildingId = 12L;
        Long userId = 15L;
        Long unitId = 101L;

        when(residentDirectory.residentsOfBuilding(buildingId))
                .thenReturn(List.of(new ExternalResidentDirectory.ResidentUnit(userId, unitId, true)));

        when(debtService.debtsOfUnit(unitId))
                .thenReturn(List.of(new ExternalDebtService.UnitDebt(2L, LocalDate.now().minusDays(1), "PENDING")));

        AccessCredential card = AccessCredential.createCloud("04A1B2C3", buildingId, userId, "Ana", null, null);
        when(credentialRepository.findAllByUserId(userId)).thenReturn(List.of(card));

        debtCheckService.refreshBuilding(buildingId);

        assertTrue(card.isSuspendedForDebt());
    }

    @Test
    void paidAllClearsDebtSuspensionOnly() {
        Long buildingId = 12L;
        Long userId = 15L;
        Long unitId = 101L;

        when(residentDirectory.residentsOfBuilding(buildingId))
                .thenReturn(List.of(new ExternalResidentDirectory.ResidentUnit(userId, unitId, true)));

        when(debtService.debtsOfUnit(unitId))
                .thenReturn(List.of(new ExternalDebtService.UnitDebt(3L, LocalDate.now().minusDays(10), "PAID")));

        AccessCredential debtSuspended = AccessCredential.createCloud("04A1B2C3", buildingId, userId, "Ana", null, null);
        debtSuspended.suspendForDebt();

        AccessCredential manualSuspended = AccessCredential.createCloud("04A1B2C4", buildingId, userId, "Ana", null, null);
        manualSuspended.changeStatus(CredentialStatus.SUSPENDED, Instant.now());

        when(credentialRepository.findAllByUserId(userId)).thenReturn(List.of(debtSuspended, manualSuspended));

        debtCheckService.refreshBuilding(buildingId);

        assertFalse(debtSuspended.isSuspendedForDebt());
        assertEquals(CredentialStatus.ACTIVE, debtSuspended.getStatus());

        // Manual suspension stays
        assertEquals(CredentialStatus.SUSPENDED, manualSuspended.getStatus());
        assertFalse(manualSuspended.isSuspendedForDebt());
    }

    @Test
    void networkErrorKeepsPreviousStateAndRecordsLastError() {
        Long buildingId = 12L;
        Long userId = 15L;
        Long unitId = 101L;

        ResidentStatus previousStatus = new ResidentStatus(userId, buildingId, Set.of(unitId), false, 0, Instant.now().minusSeconds(3600));
        when(residentStatusRepository.findByUserId(userId)).thenReturn(Optional.of(previousStatus));

        when(residentDirectory.residentsOfBuilding(buildingId))
                .thenReturn(List.of(new ExternalResidentDirectory.ResidentUnit(userId, unitId, true)));

        when(debtService.debtsOfUnit(unitId)).thenThrow(new ExternalServiceUnavailableException("Payment down"));

        debtCheckService.refreshBuilding(buildingId);

        assertFalse(previousStatus.isDelinquent());
        assertEquals("Payment down", previousStatus.getLastError());
        verify(residentStatusRepository).save(previousStatus);
        verify(edgeSyncNotifier, never()).changedForBuilding(anyLong());
    }

    @Test
    void reservationCheckFallsBackToStoredStatus() {
        Long userId = 15L;
        ResidentStatus stored = new ResidentStatus(userId, 12L, Set.of(101L), false, 0, Instant.now());
        when(residentStatusRepository.findByUserId(userId)).thenReturn(Optional.of(stored));

        when(residentDirectory.residentsOfBuilding(12L))
                .thenThrow(new ExternalServiceUnavailableException("Residential down"));

        boolean delinquent = debtCheckService.isDelinquentForReservation(userId);
        assertFalse(delinquent);
    }

    @Test
    void reservationCheckWithoutStoredStatusPropagates503() {
        Long userId = 999L;
        when(residentStatusRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(ExternalServiceUnavailableException.class, () ->
                debtCheckService.isDelinquentForReservation(userId)
        );
    }

    @Test
    void disabledCheckTreatsEveryoneAsClear() {
        properties.setDebtCheckEnabled(false);
        boolean delinquent = debtCheckService.isDelinquentForReservation(15L);
        assertFalse(delinquent);
        verifyNoInteractions(residentDirectory);
    }
}
