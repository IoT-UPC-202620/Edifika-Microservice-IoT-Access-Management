package com.edifika.access;

import com.edifika.access.access.domain.model.aggregates.DoorReservation;
import com.edifika.access.access.domain.model.aggregates.EdgeGateway;
import com.edifika.access.access.domain.model.aggregates.IotDevice;
import com.edifika.access.access.domain.model.commands.CreateDeviceCommand;
import com.edifika.access.access.domain.model.commands.CreateGatewayCommand;
import com.edifika.access.access.domain.model.valueobjects.DeviceCapability;
import com.edifika.access.access.domain.model.valueobjects.DeviceType;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.DoorReservationRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.EdgeGatewayRepository;
import com.edifika.access.access.infrastructure.persistence.jpa.repositories.IotDeviceRepository;
import com.edifika.access.support.ContractAssertions;
import com.edifika.access.support.JwtTestTokens;
import com.edifika.access.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DoorReservationControllerIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private DoorReservationRepository reservationRepository;

    @Autowired
    private IotDeviceRepository deviceRepository;

    @Autowired
    private EdgeGatewayRepository gatewayRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Long sampleDeviceId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        deviceRepository.deleteAll();
        gatewayRepository.deleteAll();

        var issue = EdgeGateway.issue(new CreateGatewayCommand("edge-gateway-01", 12L, "Torre A"));
        EdgeGateway gateway = gatewayRepository.save(issue.gateway());

        IotDevice device = new IotDevice(
                new CreateDeviceCommand("esp32-door-res", gateway.getId(), "Puerta Terraza", "Piso 15", DeviceType.DOOR, Set.of(DeviceCapability.RFID, DeviceCapability.LOCK), 5),
                gateway
        );
        IotDevice savedDevice = deviceRepository.save(device);
        sampleDeviceId = savedDevice.getId();
    }

    private HttpHeaders residentHeaders(long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.resident(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(JwtTestTokens.admin());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void concurrentOverlappingReservationsOnlyOneWins() throws Exception {
        Instant start = Instant.now().plusSeconds(3600);
        Instant end = start.plusSeconds(3600);

        Map<String, Object> req = Map.of(
                "startsAt", start.toString(),
                "endsAt", end.toString()
        );

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = List.of(
                () -> {
                    barrier.await();
                    ResponseEntity<String> res = http.postForEntity(
                            "/api/v1/iot/devices/" + sampleDeviceId + "/reservations",
                            new HttpEntity<>(req, residentHeaders(15L)),
                            String.class
                    );
                    if (res.getStatusCode() == HttpStatus.CREATED) successCount.incrementAndGet();
                    if (res.getStatusCode() == HttpStatus.CONFLICT) conflictCount.incrementAndGet();
                    return null;
                },
                () -> {
                    barrier.await();
                    ResponseEntity<String> res = http.postForEntity(
                            "/api/v1/iot/devices/" + sampleDeviceId + "/reservations",
                            new HttpEntity<>(req, residentHeaders(16L)),
                            String.class
                    );
                    if (res.getStatusCode() == HttpStatus.CREATED) successCount.incrementAndGet();
                    if (res.getStatusCode() == HttpStatus.CONFLICT) conflictCount.incrementAndGet();
                    return null;
                }
        );

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one reservation must succeed");
        assertEquals(1, conflictCount.get(), "The concurrent overlapping reservation must fail with 409 CONFLICT");
    }

    @Test
    void responsesMatchContract() throws Exception {
        Instant start = Instant.now().plusSeconds(1800);
        Instant end = start.plusSeconds(3600);

        Map<String, Object> req = Map.of(
                "startsAt", start.toString(),
                "endsAt", end.toString()
        );

        // 1. POST reservation -> DoorReservationResource
        ResponseEntity<String> createRes = http.postForEntity(
                "/api/v1/iot/devices/" + sampleDeviceId + "/reservations",
                new HttpEntity<>(req, residentHeaders(15L)),
                String.class
        );
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
        ContractAssertions.assertMatches("contracts/rest/access/DoorReservationResource.schema.json", createRes.getBody());

        var createdJson = objectMapper.readTree(createRes.getBody());
        long reservationId = createdJson.get("id").asLong();

        // 2. GET slots -> ReservationSlotResource
        ResponseEntity<String> slotsRes = http.exchange(
                "/api/v1/iot/devices/" + sampleDeviceId + "/reservations",
                HttpMethod.GET,
                new HttpEntity<>(residentHeaders(15L)),
                String.class
        );
        assertEquals(HttpStatus.OK, slotsRes.getStatusCode());
        var slotsJson = objectMapper.readTree(slotsRes.getBody());
        assertTrue(slotsJson.isArray() && !slotsJson.isEmpty());
        ContractAssertions.assertMatches("contracts/rest/access/ReservationSlotResource.schema.json", slotsJson.get(0).toString());

        // 3. POST authorization -> AuthorizationResource
        Map<String, Object> authReq = Map.of("userId", 20);
        ResponseEntity<String> authRes = http.postForEntity(
                "/api/v1/access/reservations/" + reservationId + "/authorizations",
                new HttpEntity<>(authReq, residentHeaders(15L)),
                String.class
        );
        assertEquals(HttpStatus.CREATED, authRes.getStatusCode());
        ContractAssertions.assertMatches("contracts/rest/access/AuthorizationResource.schema.json", authRes.getBody());
    }
}
