package com.edifika.access;

import com.edifika.access.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApplicationContextIT {

    @Autowired
    private TestRestTemplate http;

    @Test
    void protectedEndpointsRequireToken() {
        assertEquals(401, http.getForEntity("/api/v1/iot/devices", String.class).getStatusCode().value());
    }

    @Test
    void swaggerIsPublic() {
        assertEquals(200, http.getForEntity("/v3/api-docs", String.class).getStatusCode().value());
    }
}

