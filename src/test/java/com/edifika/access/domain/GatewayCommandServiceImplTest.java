package com.edifika.access.domain;

import com.edifika.access.access.application.internal.commandservices.GatewayCommandServiceImpl;
import com.edifika.access.access.domain.exceptions.DuplicateGatewayCodeException;
import com.edifika.access.access.domain.exceptions.GatewayNotFoundException;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.RotateGatewayApiKeyCommand;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayCommandServiceImplTest {

    @Mock
    private EdgeGatewayRepository gatewayRepository;

    private GatewayCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GatewayCommandServiceImpl(gatewayRepository);
    }

    @Test
    void createGatewaySucceeds() {
        CreateGatewayCommand cmd = new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A");
        when(gatewayRepository.existsByGatewayCode("edge-gateway-01")).thenReturn(false);
        when(gatewayRepository.save(any(EdgeGateway.class))).thenAnswer(i -> i.getArgument(0));

        var issue = service.createGateway(cmd);
        assertNotNull(issue);
        assertEquals("edge-gateway-01", issue.gateway().getGatewayCode());
        verify(gatewayRepository).save(any(EdgeGateway.class));
    }

    @Test
    void createGatewayRejectsDuplicate() {
        CreateGatewayCommand cmd = new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A");
        when(gatewayRepository.existsByGatewayCode("edge-gateway-01")).thenReturn(true);

        assertThrows(DuplicateGatewayCodeException.class, () -> service.createGateway(cmd));
        verify(gatewayRepository, never()).save(any(EdgeGateway.class));
    }

    @Test
    void rotateApiKeyNotFoundThrows() {
        when(gatewayRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(GatewayNotFoundException.class, () -> service.rotateApiKey(new RotateGatewayApiKeyCommand(99L)));
    }
}
