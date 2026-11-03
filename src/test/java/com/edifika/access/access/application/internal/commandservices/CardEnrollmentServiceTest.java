package com.edifika.access.access.application.internal.commandservices;

import com.edifika.access.access.application.internal.outboundservices.messaging.EdgeCommandPublisher;
import com.edifika.access.access.domain.exceptions.ReservationConflictException;
import com.edifika.access.access.domain.model.aggregates.AccessCredential;
import com.edifika.access.access.domain.model.aggregates.CardEnrollment;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.entities.DeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceCommandType;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.domain.model.valueobjects.EnrollmentStatus;
import com.edifika.access.access.infrastructure.external.IntegrationProperties;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.CardEnrollmentRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.shared.infrastructure.messaging.IntegrationEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CardEnrollmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T06:00:00Z");

    private CardEnrollmentRepository enrollmentRepository;
    private IotDeviceRepository deviceRepository;
    private DeviceCommandRepository commandRepository;
    private AccessCredentialRepository credentialRepository;
    private EdgeCommandPublisher publisher;
    private IotDevice door;

    private CardEnrollmentService serviceAt(Instant now) {
        IntegrationProperties properties = new IntegrationProperties();
        properties.setDeviceCommandTtlSeconds(10);
        return new CardEnrollmentService(enrollmentRepository, deviceRepository, commandRepository,
                credentialRepository, publisher, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    @BeforeEach
    void setUp() {
        enrollmentRepository = mock(CardEnrollmentRepository.class);
        deviceRepository = mock(IotDeviceRepository.class);
        commandRepository = mock(DeviceCommandRepository.class);
        credentialRepository = mock(AccessCredentialRepository.class);
        publisher = mock(EdgeCommandPublisher.class);
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var issue = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 1L, "Torre A"));
        door = new IotDevice(
                new CreateDeviceCommand("esp32-acc-01", 1L, "Puerta piscina", "Piso 1", DeviceType.DOOR,
                        Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                issue.gateway());
        ReflectionTestUtils.setField(door, "id", 1L);
        door.markOnline(NOW);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(door));
        when(deviceRepository.existsById(1L)).thenReturn(true);
    }

    private CardEnrollment activeSession(Instant startedAt) {
        CardEnrollment e = new CardEnrollment(1L, "esp32-acc-01", 1L, 1L, startedAt, CardEnrollmentService.WINDOW);
        ReflectionTestUtils.setField(e, "id", 7L);
        return e;
    }

    @Test
    void startPutsTheReaderInEnrollmentModeFor60Seconds() {
        CardEnrollment created = serviceAt(NOW).start(1L, 1L);

        assertEquals(EnrollmentStatus.ACTIVE, created.getStatus());
        assertEquals(NOW.plusSeconds(60), created.getExpiresAt());
        assertEquals("esp32-acc-01", created.getDeviceCode());

        ArgumentCaptor<DeviceCommand> command = ArgumentCaptor.forClass(DeviceCommand.class);
        verify(commandRepository).save(command.capture());
        assertEquals(DeviceCommandType.ENROLL, command.getValue().getType());
        assertEquals(command.getValue().getCommandId(), created.getCommandId());

        ArgumentCaptor<IntegrationEnvelope> envelope = ArgumentCaptor.forClass(IntegrationEnvelope.class);
        verify(publisher).publish(eq("edge-gateway-01"), eq("device-command"), envelope.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.getValue().payload();
        assertEquals("enroll", payload.get("type"));
        assertEquals(Map.of("active", true, "seconds", 60L), payload.get("params"));
    }

    @Test
    void startingAgainCancelsThePreviousSessionOfTheSameReader() {
        CardEnrollment previous = activeSession(NOW.minusSeconds(5));
        when(enrollmentRepository.findAllByDeviceIdAndStatus(1L, EnrollmentStatus.ACTIVE)).thenReturn(List.of(previous));

        serviceAt(NOW).start(1L, 1L);

        assertEquals(EnrollmentStatus.CANCELLED, previous.getStatus());
    }

    @Test
    void anOfflineReaderCannotEnterEnrollmentMode() {
        door.markOffline(NOW);

        var ex = assertThrows(ReservationConflictException.class, () -> serviceAt(NOW).start(1L, 1L));
        assertEquals("El lector no está en línea", ex.getMessage());
        verifyNoInteractions(publisher);
    }

    @Test
    void aCardReadDuringTheWindowIsCapturedNormalized() {
        CardEnrollment session = activeSession(NOW.minusSeconds(10));
        when(enrollmentRepository.findAllByDeviceCodeAndStatusOrderByStartedAtDesc("esp32-acc-01", EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(session));

        serviceAt(NOW).onCardCaptured("esp32-acc-01", "04 a1:b2-c3", NOW.minusSeconds(1));

        assertEquals(EnrollmentStatus.CAPTURED, session.getStatus());
        assertEquals("04A1B2C3", session.getCapturedUid());
    }

    @Test
    void aCardReadAfterTheWindowIsIgnoredAndTheSessionExpires() {
        CardEnrollment session = activeSession(NOW.minusSeconds(120));
        when(enrollmentRepository.findAllByDeviceCodeAndStatusOrderByStartedAtDesc("esp32-acc-01", EnrollmentStatus.ACTIVE))
                .thenReturn(List.of(session));

        serviceAt(NOW).onCardCaptured("esp32-acc-01", "04A1B2C3", NOW);

        assertEquals(EnrollmentStatus.EXPIRED, session.getStatus());
        assertNull(session.getCapturedUid());
    }

    @Test
    void currentReportsTheCredentialThatAlreadyUsesTheCapturedCard() {
        CardEnrollment session = activeSession(NOW.minusSeconds(10));
        session.capture("04A1B2C3", NOW.minusSeconds(2));
        AccessCredential existing = mock(AccessCredential.class);
        when(enrollmentRepository.findFirstByDeviceIdOrderByStartedAtDesc(1L)).thenReturn(Optional.of(session));
        when(credentialRepository.findByRfidUid("04A1B2C3")).thenReturn(Optional.of(existing));

        var view = serviceAt(NOW).current(1L).orElseThrow();

        assertSame(existing, view.existingCredential());
        assertEquals(EnrollmentStatus.CAPTURED, view.enrollment().getStatus());
    }

    @Test
    void currentExpiresASessionWhoseWindowPassed() {
        CardEnrollment session = activeSession(NOW.minusSeconds(61));
        when(enrollmentRepository.findFirstByDeviceIdOrderByStartedAtDesc(1L)).thenReturn(Optional.of(session));

        var view = serviceAt(NOW).current(1L).orElseThrow();

        assertEquals(EnrollmentStatus.EXPIRED, view.enrollment().getStatus());
    }

    @Test
    void cancelSendsTheReaderBackToNormal() {
        CardEnrollment session = activeSession(NOW.minusSeconds(10));
        when(enrollmentRepository.findFirstByDeviceIdOrderByStartedAtDesc(1L)).thenReturn(Optional.of(session));

        serviceAt(NOW).cancel(1L, 1L);

        assertEquals(EnrollmentStatus.CANCELLED, session.getStatus());
        ArgumentCaptor<IntegrationEnvelope> envelope = ArgumentCaptor.forClass(IntegrationEnvelope.class);
        verify(publisher).publish(eq("edge-gateway-01"), eq("device-command"), envelope.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.getValue().payload();
        assertEquals(Map.of("active", false), payload.get("params"));
    }
}
