package com.saga.sattolux.module.login.dto;

public record DevUserEnsureResponse(
        String userId,
        String email,
        String roleCode,
        boolean created,
        boolean rulesPrepared
) {
}
