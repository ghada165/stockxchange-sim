package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.dto.MarketHoursRequest;
import com.exchange.matching.dto.MarketStatusResponse;
import com.exchange.matching.exception.MarketClosedException;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.MarketHours;
import com.exchange.matching.persistence.entity.MarketSettingsEntity;
import com.exchange.matching.persistence.repository.MarketSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Heures d'ouverture du marche (module Marche). La valeur courante est
 * gardee en memoire (lue a chaque ordre) et persistee en base pour
 * survivre a un redemarrage.
 */
@Service
public class MarketHoursService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final MarketSettingsRepository repository;
    private final ExchangeProperties properties;
    private final ActivityLogService activityLog;

    private volatile MarketHours current;

    public MarketHoursService(MarketSettingsRepository repository, ExchangeProperties properties,
                              ActivityLogService activityLog) {
        this.repository = repository;
        this.properties = properties;
        this.activityLog = activityLog;
    }

    /** Au demarrage: reprend les horaires en base, ou les cree depuis application.yml. */
    @Transactional
    public void initialize() {
        MarketSettingsEntity settings = repository.findById(MarketSettingsEntity.SINGLETON_ID)
            .orElseGet(() -> repository.save(new MarketSettingsEntity(properties.marketHours().toMarketHours())));
        current = settings.toMarketHours();
    }

    public MarketHours current() {
        MarketHours hours = current;
        return hours != null ? hours : properties.marketHours().toMarketHours();
    }

    public boolean isOpen() {
        return current().isOpen(Instant.now());
    }

    /** @throws MarketClosedException si le marche est ferme maintenant */
    public void requireOpen() {
        MarketHours hours = current();
        if (!hours.isOpen(Instant.now())) {
            throw new MarketClosedException("Marche ferme. Horaires: " + hours.open().format(HH_MM) + " - "
                + hours.close().format(HH_MM) + " (" + hours.zone().getId() + "), jours: " + hours.tradingDays().stream().sorted().toList());
        }
    }

    public MarketStatusResponse status() {
        return MarketStatusResponse.of(current(), Instant.now());
    }

    @Transactional
    public MarketStatusResponse update(MarketHoursRequest request, String admin) {
        ZoneId zone;
        try {
            zone = ZoneId.of(request.zone());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Fuseau horaire inconnu: " + request.zone());
        }
        MarketHours hours = new MarketHours(request.open(), request.close(), zone, request.tradingDays(), request.enforced());
        MarketSettingsEntity settings = repository.findById(MarketSettingsEntity.SINGLETON_ID)
            .orElseGet(() -> new MarketSettingsEntity(hours));
        settings.apply(hours);
        repository.save(settings);
        current = hours;
        activityLog.record(admin, ActivityType.MARKET_HOURS_UPDATED, hours.open().format(HH_MM) + "-"
            + hours.close().format(HH_MM) + " " + zone.getId() + " " + hours.tradingDays().stream().sorted().toList()
            + (hours.enforced() ? " (appliquees)" : " (non appliquees: marche toujours ouvert)"));
        return status();
    }
}
