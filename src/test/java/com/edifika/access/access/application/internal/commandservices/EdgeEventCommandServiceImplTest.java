package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.messaging.ProcessedEventGuard;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.ApplyCommandResultCommand;
import com.edifika.access.access.domain.model.commands.ApplyDeviceStatusCommand;
import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCommandStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceCommandType;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.RecordAccessAttemptCommand;
import com.edifika.access.access.domain.model.entities.AccessAttempt;
import com.edifika.access.access.domain.model.events.PhysicalAccessAttemptRecordedEvent;
import com.edifika.access.access.domain.model.valueobjects.*;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessAttemptRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EdgeEventCommandServiceImplTest {

    private ProcessedEventGuard processedEventGuard;
    private AccessAttemptRepository attemptRepository;
    private IotDeviceRepository deviceRepository;
    private AccessCredentialRepository credentialRepository;
    private DeviceCommandRepository commandRepository;
    private ApplicationEventPublisher eventPublisher;
    private EdgeEventCommandServiceImpl service;

    private IotDevice sampleDevice;

    @BeforeEach
    void setUp() {
        processedEventGuard = mock(ProcessedEventGuard.class);
        attemptRepository = mock(AccessAttemptRepository.class);
        deviceRepository = mock(IotDeviceRepository.class);
        credentialRepository = mock(AccessCredentialRepository.class);
        commandRepository = mock(DeviceCommandRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new EdgeEventCommandServiceImpl(
                processedEventGuard,
                attemptRepository,
                deviceRepository,
                credentialRepository,
                commandRepository,
                eventPublisher
        );

        var issue = EdgeGateway.issue(new CreateGatewayCommand("gw-01", 12L, "Torre 1"));
        sampleDevice = new IotDevice(
                new CreateDeviceCommand("door-01", 1L, "Puerta Principal", "Norte", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                issue.gateway()
        );
    }

    @Test
    void grantedAttemptPersistsAndPublishesGranted() {
        when(processedEventGuard.firstTime("evt-1")).thenReturn(true);
        when(deviceRepository.findByDeviceCode("door-01")).thenReturn(Optional.of(sampleDevice));

        AccessCredential cred = AccessCredential.createCloud("04A1B2C3", 12L, 15L, "Ana", null, null);
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(cred));

        when(attemptRepository.save(any(AccessAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordAccessAttemptCommand cmd = new RecordAccessAttemptCommand(
                "att-1", "gw-01", "door-01", "04A1B2C3", "15", "GRANTED", "VALID_CREDENTIAL", Instant.now(), "evt-1"
        );

        service.recordAccessAttempt(cmd);

        ArgumentCaptor<AccessAttempt> captor = ArgumentCaptor.forClass(AccessAttempt.class);
        verify(attemptRepository).save(captor.capture());
        AccessAttempt saved = captor.getValue();

        assertEquals(AccessResult.GRANTED, saved.getResult());
        assertEquals(AccessReason.GRANTED, saved.getReason());
        assertEquals(15L, saved.getUserId());
        assertEquals("Ana", saved.getResidentName());

        verify(eventPublisher).publishEvent(any(PhysicalAccessAttemptRecordedEvent.class));
    }

    @Test
    void suspendedForDebtMapsToDelinquent() {
        when(processedEventGuard.firstTime("evt-2")).thenReturn(true);
        when(deviceRepository.findByDeviceCode("door-01")).thenReturn(Optional.of(sampleDevice));

        AccessCredential cred = AccessCredential.createCloud("04A1B2C3", 12L, 15L, "Ana", null, null);
        cred.suspendForDebt();
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(cred));

        when(attemptRepository.save(any(AccessAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordAccessAttemptCommand cmd = new RecordAccessAttemptCommand(
                "att-2", "gw-01", "door-01", "04A1B2C3", "15", "DENIED", "credential_suspended", Instant.now(), "evt-2"
        );

        service.recordAccessAttempt(cmd);

        ArgumentCaptor<AccessAttempt> captor = ArgumentCaptor.forClass(AccessAttempt.class);
        verify(attemptRepository).save(captor.capture());
        AccessAttempt saved = captor.getValue();

        assertEquals(AccessResult.DENIED, saved.getResult());
        assertEquals(AccessReason.DELINQUENT, saved.getReason());
    }

    @Test
    void staleOnlineAfterNewerOfflineIsIgnored() {
        when(processedEventGuard.firstTime("evt-3")).thenReturn(true);

        Instant newer = Instant.now();
        Instant older = newer.minusSeconds(60);

        sampleDevice.markOffline(newer);
        assertEquals(DeviceStatus.OFFLINE, sampleDevice.getStatus());

        when(deviceRepository.findByDeviceCode("door-01")).thenReturn(Optional.of(sampleDevice));

        ApplyDeviceStatusCommand cmd = new ApplyDeviceStatusCommand(
                "gw-01", "door-01", "online", "v1.0.0", older, "evt-3"
        );

        service.applyDeviceStatus(cmd);

        // Status should still be OFFLINE because online occurred before newer offline!
        assertEquals(DeviceStatus.OFFLINE, sampleDevice.getStatus());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void commandResultOkMarksOkAndStoresColor() {
        when(processedEventGuard.firstTime("evt-cmd-ok")).thenReturn(true);

        DeviceCommand cmd = new DeviceCommand(
                "cmd-123", 1L, DeviceCommandType.SET_LED, "{\"color\":\"#00FF88\"}", 15L, Instant.now().minusSeconds(5), Instant.now().plusSeconds(10)
        );
        when(commandRepository.findByCommandId("cmd-123")).thenReturn(Optional.of(cmd));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(sampleDevice));

        service.applyCommandResult(new ApplyCommandResultCommand(
                "gw-01", "cmd-123", "OK", null, Instant.now(), "evt-cmd-ok"
        ));

        assertEquals(DeviceCommandStatus.OK, cmd.getStatus());
        assertEquals("#00FF88", sampleDevice.getLedColor());
        verify(commandRepository).save(cmd);
        verify(deviceRepository).save(sampleDevice);
    }

    @Test
    void timeoutBecomesFailedWithDetail() {
        when(processedEventGuard.firstTime("evt-cmd-to")).thenReturn(true);

        DeviceCommand cmd = new DeviceCommand(
                "cmd-456", 1L, DeviceCommandType.SET_LED, "{\"color\":\"#FF0000\"}", 15L, Instant.now().minusSeconds(5), Instant.now().plusSeconds(10)
        );
        when(commandRepository.findByCommandId("cmd-456")).thenReturn(Optional.of(cmd));

        service.applyCommandResult(new ApplyCommandResultCommand(
                "gw-01", "cmd-456", "TIMEOUT", "Device not reachable", Instant.now(), "evt-cmd-to"
        ));

        assertEquals(DeviceCommandStatus.FAILED, cmd.getStatus());
        assertEquals("timeout", cmd.getDetail());
        verify(commandRepository).save(cmd);
    }
}
