package com.exchange.matching.dto;

import com.exchange.matching.model.MarketHours;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** Etat du marche a l'instant de la requete, avec ses horaires. */
public record MarketStatusResponse(
    boolean open,
    boolean enforced,
    LocalTime openTime,
    LocalTime closeTime,
    String zone,
    List<DayOfWeek> tradingDays,
    Instant serverTime
) {

    public static MarketStatusResponse of(MarketHours hours, Instant now) {
        return new MarketStatusResponse(hours.isOpen(now), hours.enforced(), hours.open(), hours.close(),
            hours.zone().getId(), hours.tradingDays().stream().sorted().toList(), now);
    }
}
