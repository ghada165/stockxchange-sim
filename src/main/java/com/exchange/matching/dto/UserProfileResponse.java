package com.exchange.matching.dto;

import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.UserEntity;

import java.time.Instant;

public record UserProfileResponse(
    String username,
    String displayName,
    String email,
    Role role,
    AccountStatus status,
    Instant createdAt
) {

    public static UserProfileResponse from(UserEntity user) {
        return new UserProfileResponse(user.getUsername(), user.getDisplayName(), user.getEmail(),
            user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
