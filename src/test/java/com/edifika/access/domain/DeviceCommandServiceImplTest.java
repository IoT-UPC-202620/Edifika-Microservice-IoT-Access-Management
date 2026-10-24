package com.edifika.access.domain;

import com.edifika.access.access.application.internal.commandservices.DeviceCommandServiceImpl;
import com.edifika.access.access.application.internal.outboundservices.edge.EdgeSyncNotifier;
import com.edifika.access.access.domain.exceptions.DeviceNotFoundException;
import com.edifika.access.access.domain.exceptions.DuplicateDeviceCodeException;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.DecommissionDeviceCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceStatus;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceCommandServiceImplTest {

    @Mock
    private IotDeviceRepository deviceRepository;

    @Mock
    private EdgeGatewayRepository gatewayRepository;

    @Mock
    private EdgeSyncNotifier edgeSyncNotifier;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DeviceCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCommandServiceImpl(deviceRepository, gatewayRepository, edgeSyncNotifier, eventPublisher);
    }

    private CreateDeviceCommand validCommand() {
        return new CreateDeviceCommand(
                "esp32-acc-01",
                1L,
                "Puerta piscina",
                "Piso 1",
                DeviceType.DOOR,
                Set.of(DeviceCapability.RFID, DeviceCapability.LOCK),
                5
        );
    }

    @Test
    void createDeviceSucceeds() {
        when(deviceRepository.existsByDeviceCode("esp32-acc-01")).thenReturn(false);
        EdgeGateway gw = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A")).gateway();
        when(gatewayRepository.findById(1L)).thenReturn(Optional.of(gw));
        when(deviceRepository.save(any(IotDevice.class))).thenAnswer(i -> i.getArgument(0));

        var opt = service.createDevice(validCommand());
        assertTrue(opt.isPresent());
        assertEquals("esp32-acc-01", opt.get().getDeviceCode());
        verify(edgeSyncNotifier).changed(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void createDeviceRejectsDuplicateCode() {
        when(deviceRepository.existsByDeviceCode("esp32-acc-01")).thenReturn(true);

        assertThrows(DuplicateDeviceCodeException.class, () -> service.createDevice(validCommand()));
    }

    @Test
    void decommissionSetsStatus() {
        EdgeGateway gw = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A")).gateway();
        IotDevice device = new IotDevice(validCommand(), gw);
        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(IotDevice.class))).thenAnswer(i -> i.getArgument(0));

        service.decommissionDevice(new DecommissionDeviceCommand(10L));
        assertEquals(DeviceStatus.DECOMMISSIONED, device.getStatus());
        verify(edgeSyncNotifier).changed(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void decommissionNotFoundThrows() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFoundException.class, () -> service.decommissionDevice(new DecommissionDeviceCommand(99L)));
    }
}
