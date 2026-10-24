package com.edifika.access.access.infrastructure.external;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Component
public class ServiceTokenProvider {

    private final SecretKey signingKey;
    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    public ServiceTokenProvider(@Value("${jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized String token() {
        Instant now = Instant.now();
        if (cachedToken != null && now.isBefore(expiresAt.minus(30, ChronoUnit.SECONDS))) {
            return cachedToken;
        }

        Instant expiration = now.plus(5, ChronoUnit.MINUTES);
        cachedToken = Jwts.builder()
                .subject("iot-access-management")
                .claim("user_id", 0L)
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(signingKey)
                .compact();
        expiresAt = expiration;

        return cachedToken;
    }
}
