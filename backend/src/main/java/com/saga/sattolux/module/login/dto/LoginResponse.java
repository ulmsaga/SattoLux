package com.saga.sattolux.module.login.dto;

public record LoginResponse(
        String authSessionToken,
        boolean otpEnabled
) {
}
