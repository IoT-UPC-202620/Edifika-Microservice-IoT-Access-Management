package com.edifika.access.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

public class JwtTestTokens {
    public static final String SECRET = "edifika-secret-key-must-be-at-least-32-characters-long";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    public static String admin() {
        return Jwts.builder()
                .subject("admin@edifika.com")
                .claim("user_id", 1L)
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                .signWith(KEY)
                .compact();
    }

    public static String resident(long userId) {
        return Jwts.builder()
                .subject("resident" + userId + "@edifika.com")
                .claim("user_id", userId)
                .claim("roles", List.of("RESIDENT"))
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
                .signWith(KEY)
                .compact();
    }
}
