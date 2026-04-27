package com.saga.sattolux.module.login.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String sessionId,
        @NotBlank String encryptedPassword,
        @NotBlank String userId
) {
}
