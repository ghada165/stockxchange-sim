package com.exchange.matching.persistence.entity;

import com.exchange.matching.model.MarketHours;
import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

/** Heures d'ouverture du marche, modifiables par un administrateur (ligne unique id = 1). */
@Entity
@Table(name = "market_settings")
public class MarketSettingsEntity {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(nullable = false)
    private LocalTime openTime;

    @Column(nullable = false)
    private LocalTime closeTime;

    @Column(nullable = false)
    private String timeZone;

    /** Jours separes par des virgules, ex. "MONDAY,TUESDAY". */
    @Column(nullable = false)
    private String tradingDays;

    @Column(nullable = false)
    private boolean enforced;

    protected MarketSettingsEntity() {
    }

    public MarketSettingsEntity(MarketHours hours) {
        this.id = SINGLETON_ID;
        apply(hours);
    }

    public void apply(MarketHours hours) {
        this.openTime = hours.open();
        this.closeTime = hours.close();
        this.timeZone = hours.zone().getId();
        this.tradingDays = hours.tradingDays().stream()
            .sorted()
            .map(DayOfWeek::name)
            .collect(Collectors.joining(","));
        this.enforced = hours.enforced();
    }

    public MarketHours toMarketHours() {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(tradingDays.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(DayOfWeek::valueOf)
            .forEach(days::add);
        return new MarketHours(openTime, closeTime, ZoneId.of(timeZone), days, enforced);
    }

    public Integer getId() { return id; }
}
