package com.exchange.matching.config;

import com.exchange.matching.dto.PlaceOrderRequest;
import com.exchange.matching.engine.MatchingEngine;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.service.TradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Teneur de marche de demonstration: au demarrage, pour chaque symbole dont
 * le carnet est vide, place 5 ordres d'achat sous le prix de reference et
 * 5 ordres de vente au-dessus. Le carnet n'est donc jamais vide en demo, et
 * un ordre MARKET a toujours une contrepartie.
 *
 * Les ordres passent par TradingService comme ceux d'un vrai utilisateur
 * (reservation des fonds, persistance): aucun raccourci.
 * Desactivable avec exchange.seed-market=false.
 */
@Component
@Order(2)
public class MarketSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketSeeder.class);

    static final String MARKET_MAKER = "market-maker";
    private static final int LEVELS = 5;
    private static final BigDecimal STEP = new BigDecimal("0.002"); // 0,2 % entre deux niveaux

    private final ExchangeProperties properties;
    private final MatchingEngine matchingEngine;
    private final TradingService tradingService;

    public MarketSeeder(ExchangeProperties properties, MatchingEngine matchingEngine,
                        TradingService tradingService) {
        this.properties = properties;
        this.matchingEngine = matchingEngine;
        this.tradingService = tradingService;
    }

    @Override
    public void run(String... args) {
        if (!properties.seedMarket()) {
            return;
        }
        properties.symbols().forEach((symbol, referencePrice) -> {
            boolean empty = matchingEngine.readBook(symbol, book -> book.isEmpty());
            if (!empty) {
                return;
            }
            for (int level = 1; level <= LEVELS; level++) {
                BigDecimal offset = STEP.multiply(BigDecimal.valueOf(level));
                BigDecimal quantity = BigDecimal.valueOf(2L * level + 2); // 4, 6, 8, 10, 12
                place(symbol, OrderSide.BUY, referencePrice.multiply(BigDecimal.ONE.subtract(offset)), quantity);
                place(symbol, OrderSide.SELL, referencePrice.multiply(BigDecimal.ONE.add(offset)), quantity);
            }
            log.info("Carnet {} amorce autour de {} $", symbol, referencePrice);
        });
    }

    private void place(String symbol, OrderSide side, BigDecimal rawPrice, BigDecimal quantity) {
        BigDecimal price = rawPrice.setScale(2, RoundingMode.HALF_UP);
        tradingService.placeOrder(MARKET_MAKER, new PlaceOrderRequest(symbol, side, OrderType.LIMIT, price, quantity));
    }
}
