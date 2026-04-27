package com.saga.sattolux.module.login.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
