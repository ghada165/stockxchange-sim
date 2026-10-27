package com.exchange.matching.dto;

import com.exchange.matching.model.ActivityType;
import com.exchange.matching.persistence.entity.ActivityLogEntity;

import java.time.Instant;

public record ActivityLogResponse(
    Long id,
    Instant occurredAt,
    String actor,
    ActivityType action,
    String details
) {

    public static ActivityLogResponse from(ActivityLogEntity log) {
        return new ActivityLogResponse(log.getId(), log.getOccurredAt(), log.getActor(), log.getAction(), log.getDetails());
    }
}
