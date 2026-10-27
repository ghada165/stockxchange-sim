package com.exchange.matching.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Horaires d'ouverture du marche. Record immuable avec un comportement
 * metier: savoir si le marche est ouvert a un instant donne.
 *
 * Si {@code enforced} vaut false, le marche est toujours ouvert (pratique
 * pour une demo en dehors des heures de bourse).
 */
public record MarketHours(
    LocalTime open,
    LocalTime close,
    ZoneId zone,
    Set<DayOfWeek> tradingDays,
    boolean enforced
) {

    public MarketHours {
        if (!open.isBefore(close)) {
            throw new IllegalArgumentException("L'heure d'ouverture doit preceder l'heure de fermeture");
        }
        if (tradingDays.isEmpty()) {
            throw new IllegalArgumentException("Au moins un jour d'ouverture est requis");
        }
        tradingDays = Set.copyOf(tradingDays);
    }

    public boolean isOpen(Instant instant) {
        if (!enforced) {
            return true;
        }
        ZonedDateTime local = instant.atZone(zone);
        LocalTime time = local.toLocalTime();
        return tradingDays.contains(local.getDayOfWeek())
            && !time.isBefore(open)
            && time.isBefore(close);
    }
}
