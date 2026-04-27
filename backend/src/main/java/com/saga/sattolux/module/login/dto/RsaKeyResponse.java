package com.saga.sattolux.module.login.dto;

public record RsaKeyResponse(
        String sessionId,
        String publicKey
) {
}
