package com.exchange.matching.config;

import com.exchange.matching.dto.PlaceOrderRequest;
import com.exchange.matching.engine.MatchingEngine;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.service.SymbolService;
import com.exchange.matching.service.TradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Teneur de marche de demonstration: pour chaque symbole actif dont le
 * carnet est vide, place 5 ordres d'achat sous le prix de reference et 5
 * ordres de vente au-dessus. Le carnet n'est donc jamais vide en demo, et
 * un ordre MARKET a toujours une contrepartie. Aussi appele a l'introduction
 * en bourse d'un nouveau symbole par l'administrateur.
 *
 * Les ordres passent par TradingService comme ceux d'un vrai utilisateur
 * (reservation des fonds, persistance): aucun raccourci.
 * Desactivable avec exchange.seed-market=false.
 */
@Component
@Order(2)
public class MarketSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketSeeder.class);

    private static final int LEVELS = 5;
    private static final BigDecimal STEP = new BigDecimal("0.002"); // 0,2 % entre deux niveaux

    @Autowired
    private ExchangeProperties properties;
    @Autowired
    private MatchingEngine matchingEngine;
    @Autowired
    private TradingService tradingService;
    @Autowired
    private SymbolService symbolService;

    @Override
    public void run(String... args) {
        for (SymbolEntity symbol : symbolService.list()) {
            if (symbol.isTradable()) {
                seed(symbol.getSymbol(), symbol.getReferencePrice());
            }
        }
    }

    /** Amorce le carnet d'un symbole s'il est vide (et si l'amorcage est active). */
    public void seed(String symbol, BigDecimal referencePrice) {
        if (!properties.seedMarket()) {
            return;
        }
        boolean empty = matchingEngine.readBook(symbol, book -> book.isEmpty());
        if (!empty) {
            return;
        }
        try {
            for (int level = 1; level <= LEVELS; level++) {
                BigDecimal offset = STEP.multiply(BigDecimal.valueOf(level));
                BigDecimal quantity = BigDecimal.valueOf(2L * level + 2); // 4, 6, 8, 10, 12
                place(symbol, OrderSide.BUY, referencePrice.multiply(BigDecimal.ONE.subtract(offset)), quantity);
                place(symbol, OrderSide.SELL, referencePrice.multiply(BigDecimal.ONE.add(offset)), quantity);
            }
            log.info("Carnet {} amorce autour de {} $", symbol, referencePrice);
        } catch (RuntimeException e) {
            // L'amorcage est un confort de demo: il ne doit jamais empecher le demarrage.
            log.warn("Amorcage du carnet {} interrompu: {}", symbol, e.getMessage());
        }
    }

    private void place(String symbol, OrderSide side, BigDecimal rawPrice, BigDecimal quantity) {
        BigDecimal price = rawPrice.setScale(2, RoundingMode.HALF_UP);
        tradingService.placeOrder(TradingService.MARKET_MAKER,
            new PlaceOrderRequest(symbol, side, OrderType.LIMIT, price, quantity));
    }
}
