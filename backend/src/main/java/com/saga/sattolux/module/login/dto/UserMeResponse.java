package com.saga.sattolux.module.login.dto;

public record UserMeResponse(
        Long userSeq,
        String userId,
        String roleCode
) {
}
