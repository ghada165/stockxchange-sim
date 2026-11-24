package com.exchange.matching.dto;

import com.exchange.matching.model.Role;
import jakarta.validation.constraints.NotNull;

/** Attribution de role a un utilisateur (administrateur). */
public record RoleUpdateRequest(
    @NotNull(message = "role est requis (TRADER ou ADMIN)") Role role
) {}
