package com.edifika.access.performance;

import com.edifika.access.access.infrastructure.persistence.jpa.repositories.AccessCredentialRepository;
import com.edifika.access.support.JwtTestTokens;
import com.edifika.access.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public class CreateCredentialLatencyIT {

    private static final Logger log = LoggerFactory.getLogger(CreateCredentialLatencyIT.class);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private AccessCredentialRepository credentialRepository;

    @BeforeEach
    void setUp() {
        credentialRepository.deleteAll();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(JwtTestTokens.admin());
        return headers;
    }

    @Test
    void createCredentialLatencyP95Under300ms() {
        // Warm up with 1 request
        Map<String, Object> warmup = Map.of(
                "rfidUid", "04A1B200",
                "buildingId", 12L,
                "userId", 999L,
                "holderName", "Warmup User",
                "validFrom", "2026-10-23T15:04:05Z",
                "validUntil", "2027-10-23T15:04:05Z"
        );
        http.postForEntity("/api/v1/access/credentials", new HttpEntity<>(warmup, adminHeaders()), String.class);

        int count = 20;
        List<Double> latenciesMs = new ArrayList<>(count);

        for (int i = 1; i <= count; i++) {
            String uid = String.format("04A1B2%02X", i);
            Map<String, Object> req = Map.of(
                    "rfidUid", uid,
                    "buildingId", 12L,
                    "userId", 1000L + i,
                    "holderName", "Resident " + i,
                    "validFrom", "2026-10-23T15:04:05Z",
                    "validUntil", "2027-10-23T15:04:05Z"
            );

            long start = System.nanoTime();
            ResponseEntity<String> response = http.postForEntity(
                    "/api/v1/access/credentials",
                    new HttpEntity<>(req, adminHeaders()),
                    String.class
            );
            long elapsedNanos = System.nanoTime() - start;
            double elapsedMs = elapsedNanos / 1_000_000.0;

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            latenciesMs.add(elapsedMs);
            log.info("Request {}/{} latency: {} ms", i, count, String.format("%.2f", elapsedMs));
        }

        Collections.sort(latenciesMs);
        int p95Index = (int) Math.ceil(0.95 * count) - 1; // 19th element (index 18)
        double p95 = latenciesMs.get(p95Index);
        double avg = latenciesMs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        log.info("CreateCredentialLatency benchmark results: count={}, avg={:.2f}ms, min={:.2f}ms, max={:.2f}ms, p95={:.2f}ms",
                count, avg, latenciesMs.get(0), latenciesMs.get(count - 1), p95);

        assertTrue(p95 < 300.0, "p95 latency (" + p95 + " ms) exceeded 300 ms threshold");
    }
}
