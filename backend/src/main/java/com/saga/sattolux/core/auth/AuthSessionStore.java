package com.saga.sattolux.core.auth;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthSessionStore {

    private static final long SESSION_TTL_MINUTES = 10;

    private final Map<String, AuthSession> sessions = new ConcurrentHashMap<>();

    public String createSession(Long userSeq, boolean otpEnabled) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new AuthSession(userSeq, otpEnabled, LocalDateTime.now().plusMinutes(SESSION_TTL_MINUTES)));
        return token;
    }

    public AuthSession getValidSession(String token) {
        AuthSession session = sessions.get(token);
        if (session == null) {
            return null;
        }

        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            return null;
        }

        return session;
    }

    public AuthSession consumeSession(String token) {
        AuthSession session = getValidSession(token);
        if (session != null) {
            sessions.remove(token);
        }
        return session;
    }

    public record AuthSession(Long userSeq, boolean otpEnabled, LocalDateTime expiresAt) {
    }
}
