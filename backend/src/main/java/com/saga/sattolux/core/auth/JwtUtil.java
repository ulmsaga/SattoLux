package com.saga.sattolux.core.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtUtil(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.access-token-validity-ms}") long accessTokenValidityMs,
            @Value("${security.jwt.refresh-token-validity-ms}") long refreshTokenValidityMs
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes.");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String generateAccessToken(Long userSeq, String userId, String roleCode) {
        return Jwts.builder()
                .subject(String.valueOf(userSeq))
                .claim("userId", userId)
                .claim("role", roleCode)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenValidityMs))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userSeq) {
        return Jwts.builder()
                .subject(String.valueOf(userSeq))
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenValidityMs))
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserSeq(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    public long getRefreshTokenValidityMs() {
        return refreshTokenValidityMs;
    }
}
