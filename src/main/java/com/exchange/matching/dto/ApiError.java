package com.exchange.matching.dto;

import java.time.Instant;

/** Corps JSON renvoye pour toute erreur de l'API (voir GlobalExceptionHandler). */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message
) {}
