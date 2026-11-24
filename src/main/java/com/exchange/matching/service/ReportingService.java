package com.exchange.matching.service;

import com.exchange.matching.dto.CandleResponse;
import com.exchange.matching.dto.MyTradeResponse;
import com.exchange.matching.dto.SymbolStatsResponse;
import com.exchange.matching.dto.TradeHistoryResponse;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.persistence.entity.TradeEntity;
import com.exchange.matching.persistence.repository.TradeRepository;
import com.exchange.matching.reporting.CandleAggregator;
import com.exchange.matching.reporting.CandleAggregator.PricePoint;
import com.exchange.matching.reporting.TradeStats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Module Historique & Reporting: historique de prix, statistiques, releves. */
@Service
@Transactional(readOnly = true)
public class ReportingService {

    /** Intervalles de chandeliers acceptes, avec la fenetre d'historique associee. */
    private static final Map<String, Duration[]> INTERVALS = Map.of(
        "1m", new Duration[]{Duration.ofMinutes(1), Duration.ofHours(6)},
        "5m", new Duration[]{Duration.ofMinutes(5), Duration.ofHours(24)},
        "15m", new Duration[]{Duration.ofMinutes(15), Duration.ofDays(3)},
        "1h", new Duration[]{Duration.ofHours(1), Duration.ofDays(7)}
    );

    private static final Duration STATS_PERIOD = Duration.ofHours(24);

    @Autowired
    private TradeRepository tradeRepository;
    @Autowired
    private SymbolService symbolService;

    /** Historique de prix en chandeliers OHLC. */
    public List<CandleResponse> candles(String symbol, String interval) {
        symbolService.require(symbol);
        Duration[] config = INTERVALS.get(interval);
        if (config == null) {
            throw new IllegalArgumentException("Intervalle inconnu: " + interval + " (valeurs: 1m, 5m, 15m, 1h)");
        }
        Instant since = Instant.now().minus(config[1]);
        List<PricePoint> points = tradeRepository.findBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(symbol, since)
            .stream()
            .map(t -> new PricePoint(t.getExecutedAt(), t.getPrice(), t.getQuantity()))
            .toList();
        return CandleAggregator.aggregate(points, config[0]);
    }

    /** Statistiques d'un symbole sur les dernieres 24 h. */
    public SymbolStatsResponse stats(String symbol) {
        symbolService.require(symbol);
        Instant since = Instant.now().minus(STATS_PERIOD);
        TradeStats stats = tradeRepository.statsForSymbol(symbol, since);
        BigDecimal open = tradeRepository.findFirstBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(symbol, since)
            .map(TradeEntity::getPrice).orElse(null);
        BigDecimal last = tradeRepository.findTopBySymbolOrderByExecutedAtDesc(symbol)
            .map(TradeEntity::getPrice).orElse(null);
        BigDecimal average = stats.volume().signum() == 0 ? null
            : stats.turnover().divide(stats.volume(), 4, RoundingMode.HALF_UP);
        BigDecimal change = open == null || last == null || open.signum() == 0 ? null
            : last.subtract(open).multiply(BigDecimal.valueOf(100)).divide(open, 2, RoundingMode.HALF_UP);
        return new SymbolStatsResponse(symbol, since, stats.tradeCount(), stats.volume(), stats.turnover(),
            average, open, stats.high(), stats.low(), last, change);
    }

    /** Statistiques globales de la plateforme sur les dernieres 24 h (tableau de bord admin). */
    public TradeStats globalStats() {
        return tradeRepository.statsForAll(Instant.now().minus(STATS_PERIOD));
    }

    /** Releve d'un trader: ses executions (achat ou vente de son point de vue) et leur synthese. */
    public TradeHistoryResponse history(String username) {
        List<MyTradeResponse> trades = tradeRepository.findTop500ByBuyerIdOrSellerIdOrderByExecutedAtDesc(username, username)
            .stream()
            .map(t -> {
                // Un utilisateur ne trade jamais contre lui-meme (anti-auto-trade): un seul cote est le sien.
                OrderSide side = username.equals(t.getBuyerId()) ? OrderSide.BUY : OrderSide.SELL;
                return new MyTradeResponse(t.getId(), t.getSymbol(), side, t.getPrice(), t.getQuantity(),
                    t.getPrice().multiply(t.getQuantity()), t.getExecutedAt());
            })
            .toList();

        int buyCount = 0;
        int sellCount = 0;
        BigDecimal bought = BigDecimal.ZERO;
        BigDecimal sold = BigDecimal.ZERO;
        for (MyTradeResponse trade : trades) {
            switch (trade.side()) {
                case BUY -> {
                    buyCount++;
                    bought = bought.add(trade.amount());
                }
                case SELL -> {
                    sellCount++;
                    sold = sold.add(trade.amount());
                }
            }
        }
        var summary = new TradeHistoryResponse.Summary(trades.size(), buyCount, sellCount, bought, sold, sold.subtract(bought));
        return new TradeHistoryResponse(username, Instant.now(), summary, trades);
    }
}
