package com.exchange.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

/** Nouvelles heures d'ouverture (administrateur). Heures au format "HH:mm". */
public record MarketHoursRequest(
    @NotNull(message = "open est requis") LocalTime open,
    @NotNull(message = "close est requis") LocalTime close,
    @NotBlank(message = "zone est requise (ex. Europe/Paris)") String zone,
    @NotEmpty(message = "au moins un jour d'ouverture est requis") Set<DayOfWeek> tradingDays,
    boolean enforced
) {}
