package com.exchange.matching.dto;

import java.time.Instant;

/** Reponse de connexion / inscription: le jeton a envoyer dans "Authorization: Bearer ...". */
public record AuthResponse(
    String token,
    Instant expiresAt,
    UserProfileResponse user
) {}
