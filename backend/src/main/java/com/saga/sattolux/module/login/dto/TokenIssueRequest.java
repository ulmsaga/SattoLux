package com.saga.sattolux.module.login.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenIssueRequest(
        @NotBlank String authSessionToken
) {
}
