package com.exchange.matching.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class MarketHoursTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private MarketHours weekdays(boolean enforced) {
        return new MarketHours(LocalTime.of(9, 0), LocalTime.of(17, 30), PARIS,
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), enforced);
    }

    @Test
    void ouvert_un_mardi_a_10h_heure_de_paris() {
        // mardi 27 octobre 2026, 10:00 a Paris = 09:00 UTC (heure d'hiver)
        assertThat(weekdays(true).isOpen(Instant.parse("2026-10-27T09:00:00Z"))).isTrue();
    }

    @Test
    void ferme_avant_l_ouverture_et_a_la_fermeture_pile() {
        assertThat(weekdays(true).isOpen(Instant.parse("2026-10-27T07:59:00Z"))).isFalse(); // 08:59 Paris
        assertThat(weekdays(true).isOpen(Instant.parse("2026-10-27T16:30:00Z"))).isFalse(); // 17:30 Paris
    }

    @Test
    void ferme_le_week_end() {
        assertThat(weekdays(true).isOpen(Instant.parse("2026-10-31T10:00:00Z"))).isFalse(); // samedi
    }

    @Test
    void toujours_ouvert_si_les_horaires_ne_sont_pas_appliques() {
        assertThat(weekdays(false).isOpen(Instant.parse("2026-10-31T23:00:00Z"))).isTrue();
    }

    @Test
    void horaires_incoherents_refuses() {
        try {
            new MarketHours(LocalTime.of(18, 0), LocalTime.of(9, 0), PARIS, EnumSet.of(DayOfWeek.MONDAY), true);
            throw new AssertionError("une ouverture apres la fermeture doit etre refusee");
        } catch (IllegalArgumentException expected) {
            assertThat(expected.getMessage()).isEqualTo("L'heure d'ouverture doit preceder l'heure de fermeture");
        }
    }
}
