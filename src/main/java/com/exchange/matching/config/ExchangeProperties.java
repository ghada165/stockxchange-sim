package com.exchange.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration metier de la bourse, lue depuis application.yml (prefixe
 * "exchange"). C'est un record: Spring Boot remplit ses composants par le
 * constructeur ("constructor binding"), et la configuration ne peut plus
 * etre modifiee ensuite.
 *
 * @param startingBalance cash offert a tout nouveau compte (demo, pas un vrai depot)
 * @param startingShares  actions offertes pour CHAQUE symbole a tout nouveau compte
 * @param symbols         symboles cotes -> prix de reference (sert a amorcer le carnet)
 * @param seedMarket      place des ordres de teneur de marche au demarrage si le carnet est vide
 */
@ConfigurationProperties(prefix = "exchange")
public record ExchangeProperties(
    BigDecimal startingBalance,
    BigDecimal startingShares,
    Map<String, BigDecimal> symbols,
    boolean seedMarket
) {

    /** Constructeur compact: valeurs par defaut si une cle manque dans le YAML. */
    public ExchangeProperties {
        if (startingBalance == null) startingBalance = new BigDecimal("100000");
        if (startingShares == null) startingShares = new BigDecimal("100");
        symbols = symbols == null ? Map.of() : new LinkedHashMap<>(symbols);
    }

    public boolean isListed(String symbol) {
        return symbols.containsKey(symbol);
    }
}
