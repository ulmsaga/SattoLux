package com.saga.sattolux.module.login.dto;

import jakarta.validation.constraints.NotBlank;

public record PinLoginRequest(
        @NotBlank String sessionId,
        @NotBlank String userId,
        @NotBlank String encryptedPin
) {
}
