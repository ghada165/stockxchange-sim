package com.exchange.matching.dto;

import com.exchange.matching.model.AccountStatus;
import jakarta.validation.constraints.NotNull;

/** Activer ou suspendre un symbole ou un utilisateur. */
public record StatusUpdateRequest(
    @NotNull(message = "status est requis (ACTIVE ou SUSPENDED)") AccountStatus status
) {}
