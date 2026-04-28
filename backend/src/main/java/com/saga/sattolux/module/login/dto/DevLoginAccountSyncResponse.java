package com.saga.sattolux.module.login.dto;

import java.time.LocalDateTime;

public record DevLoginAccountSyncResponse(
        String adminUserId,
        boolean adminCreated,
        String adminRoleCode,
        String loginUserId,
        boolean loginUserCreated,
        String loginUserRoleCode,
        LocalDateTime syncedAt
) {
}
