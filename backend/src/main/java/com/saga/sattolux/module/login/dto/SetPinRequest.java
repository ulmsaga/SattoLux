package com.saga.sattolux.module.login.dto;

import jakarta.validation.constraints.NotBlank;

public record SetPinRequest(
        @NotBlank String sessionId,
        @NotBlank String encryptedPin
) {
}
