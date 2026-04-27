package com.saga.sattolux.module.login.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank String authSessionToken,
        @NotBlank String code
) {
}
