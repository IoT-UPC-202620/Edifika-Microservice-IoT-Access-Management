package com.edifika.access;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.commands.CreateReservationCommand;
import com.edifika.access.access.domain.model.valueobjects.AccessMode;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DeviceCommandRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.support.ContractAssertions;
import com.edifika.access.support.JwtTestTokens;
import com.edifika.access.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DeviceControlControllerIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private DeviceCommandRepository commandRepository;

    @Autowired
    private DoorReservationRepository reservationRepository;

    @Autowired
    private IotDeviceRepository deviceRepository;

    @Autowired
    private EdgeGatewayRepository gatewayRepository;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private EdgeGateway sampleGateway;
    private IotDevice sampleDevice;
    private DoorReservation sampleReservation;

    @BeforeEach
    void setUp() {
        commandRepository.deleteAll();
        reservationRepository.deleteAll();
        deviceRepository.deleteAll();
        gatewayRepository.deleteAll();

        var issue = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A"));
        sampleGateway = gatewayRepository.save(issue.gateway());

        IotDevice device = new IotDevice(
                new CreateDeviceCommand("esp32-ctrl-it", sampleGateway.getId(), "Puerta Terraza", "Piso 15", DeviceType.DOOR,
                        Set.of(DeviceCapability.RFID, DeviceCapability.LOCK, DeviceCapability.RGB_LED), 5),
                sampleGateway
        );
        device.markOnline(Instant.now());
        sampleDevice = deviceRepository.save(device);

        // Create an active reservation for holder 15
        DoorReservation res = DoorReservation.create(new CreateReservationCommand(
                sampleDevice.getId(), 15L, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), 15L
        ));
        res.activate();
        sampleReservation = reservationRepository.save(res);

        sampleDevice.setAccessMode(AccessMode.RESERVED, sampleReservation.getId());
        sampleDevice = deviceRepository.save(sampleDevice);
    }

    private HttpHeaders residentHeaders(long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.resident(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void holderGets202AndOrderIsPublished() throws Exception {
        String testQueue = "test.spy.device-command";
        amqpAdmin.declareQueue(new Queue(testQueue, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(testQueue, true, false, false))
                .to(new TopicExchange("edifika.edge.commands"))
                .with(sampleGateway.getGatewayCode() + ".#"));

        Map<String, Object> req = Map.of("color", "#00ff88");

        ResponseEntity<String> response = http.exchange(
                "/api/v1/iot/devices/" + sampleDevice.getId() + "/led",
                HttpMethod.PUT,
                new HttpEntity<>(req, residentHeaders(15L)),
                String.class
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        ContractAssertions.assertMatches("contracts/rest/access/DeviceCommandResource.schema.json", response.getBody());

        Message received = rabbitTemplate.receive(testQueue, 5000);
        assertNotNull(received, "Should receive DeviceCommandRequested message on spy queue");

        String body = new String(received.getBody(), StandardCharsets.UTF_8);
        var envelope = objectMapper.readTree(body);
        assertEquals("DeviceCommandRequested", envelope.get("eventType").asText());
        assertEquals(sampleGateway.getGatewayCode() + ".device-command", received.getMessageProperties().getReceivedRoutingKey());

        ContractAssertions.assertMatches("contracts/payloads/DeviceCommandRequested.schema.json", envelope.get("payload").toString());

        amqpAdmin.deleteQueue(testQueue);
    }
}
