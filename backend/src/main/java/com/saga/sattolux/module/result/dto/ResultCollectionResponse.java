package com.saga.sattolux.module.result.dto;

import java.time.LocalDateTime;

public record ResultCollectionResponse(
        int savedCount,
        LocalDateTime collectedAt
) {
}
