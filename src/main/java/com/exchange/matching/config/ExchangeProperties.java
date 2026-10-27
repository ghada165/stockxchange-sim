package com.exchange.matching.config;

import com.exchange.matching.model.MarketHours;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

/**
 * Configuration metier de la bourse, lue depuis application.yml (prefixe
 * "exchange"). Records imbriques: Spring Boot remplit chaque niveau par son
 * constructeur ("constructor binding"), et rien n'est modifiable ensuite.
 *
 * @param startingBalance cash offert a tout nouveau compte trader (demo, pas un vrai depot)
 * @param startingShares  actions offertes pour CHAQUE symbole a tout nouveau compte
 * @param symbols         symboles crees au premier demarrage (ensuite geres par l'admin)
 * @param seedMarket      place des ordres de teneur de marche si un carnet est vide
 * @param seedDemoUsers   cree les comptes de demo (alice, bob, carol) au premier demarrage
 * @param marketHours     heures d'ouverture initiales (ensuite modifiables par l'admin)
 * @param security        parametres JWT et compte administrateur initial
 */
@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(
    BigDecimal startingBalance,
    BigDecimal startingShares,
    List<ListedSymbol> symbols,
    boolean seedMarket,
    boolean seedDemoUsers,
    MarketHoursSettings marketHours,
    Security security
) {

    public ExchangeProperties {
        if (startingBalance == null) startingBalance = new BigDecimal("100000");
        if (startingShares == null) startingShares = new BigDecimal("100");
        symbols = symbols == null ? List.of() : List.copyOf(symbols);
        if (marketHours == null) marketHours = new MarketHoursSettings(null, null, null, null, false);
        if (security == null) security = new Security(null, null, null);
    }

    /** Symbole cote au premier demarrage. */
    public record ListedSymbol(String symbol, String name, BigDecimal referencePrice) {}

    /** Heures d'ouverture initiales, en texte pour rester lisibles dans le YAML. */
    public record MarketHoursSettings(String open, String close, String zone, List<DayOfWeek> days, boolean enforced) {

        public MarketHours toMarketHours() {
            List<DayOfWeek> tradingDays = days == null || days.isEmpty()
                ? List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                : days;
            return new MarketHours(
                LocalTime.parse(open == null ? "09:00" : open),
                LocalTime.parse(close == null ? "17:30" : close),
                ZoneId.of(zone == null ? "Europe/Paris" : zone),
                EnumSet.copyOf(tradingDays),
                enforced
            );
        }
    }

    /**
     * @param jwtSecret     cle HMAC de signature des jetons (32 caracteres minimum)
     * @param tokenValidity duree de validite d'un jeton
     * @param adminPassword mot de passe du compte "admin" cree au premier demarrage
     */
    public record Security(String jwtSecret, Duration tokenValidity, String adminPassword) {

        public Security {
            if (jwtSecret == null) jwtSecret = "dev-only-secret-change-me-in-production-0123456789";
            if (jwtSecret.length() < 32) {
                throw new IllegalArgumentException("exchange.security.jwt-secret doit faire au moins 32 caracteres");
            }
            if (tokenValidity == null) tokenValidity = Duration.ofHours(8);
            if (adminPassword == null) adminPassword = "admin123";
        }
    }
}
