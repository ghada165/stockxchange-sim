package com.exchange.matching.controller;

import com.exchange.matching.dto.CandleResponse;
import com.exchange.matching.dto.MarketStatusResponse;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.OrderBookResponse;
import com.exchange.matching.dto.SymbolStatsResponse;
import com.exchange.matching.dto.TradeResponse;
import com.exchange.matching.service.MarketHoursService;
import com.exchange.matching.service.ReportingService;
import com.exchange.matching.service.TradingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Module Marche: donnees publiques, accessibles aux visiteurs sans connexion. */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final TradingService tradingService;
    private final ReportingService reportingService;
    private final MarketHoursService marketHours;

    public MarketController(TradingService tradingService, ReportingService reportingService,
                            MarketHoursService marketHours) {
        this.tradingService = tradingService;
        this.reportingService = reportingService;
        this.marketHours = marketHours;
    }

    /** GET /api/market/status : marche ouvert ou ferme, et ses horaires. */
    @GetMapping("/status")
    public MarketStatusResponse status() {
        return marketHours.status();
    }

    /** GET /api/market/symbols : symboles cotes avec statut, dernier prix et meilleurs prix. */
    @GetMapping("/symbols")
    public List<MarketSummaryResponse> symbols() {
        return tradingService.getMarketSummaries();
    }

    /** GET /api/market/{symbol}/orderbook : carnet d'ordres agrege par niveau de prix. */
    @GetMapping("/{symbol}/orderbook")
    public OrderBookResponse orderBook(@PathVariable String symbol) {
        return tradingService.getOrderBookSnapshot(symbol);
    }

    /** GET /api/market/{symbol}/trades : 50 dernieres transactions (historique par symbole). */
    @GetMapping("/{symbol}/trades")
    public List<TradeResponse> recentTrades(@PathVariable String symbol) {
        return tradingService.getRecentTrades(symbol).stream()
            .map(TradeResponse::from)
            .toList();
    }

    /** GET /api/market/{symbol}/candles?interval=1m : historique de prix (1m, 5m, 15m, 1h). */
    @GetMapping("/{symbol}/candles")
    public List<CandleResponse> candles(@PathVariable String symbol,
                                        @RequestParam(defaultValue = "1m") String interval) {
        return reportingService.candles(symbol, interval);
    }

    /** GET /api/market/{symbol}/stats : volume, prix moyen, plus haut, plus bas sur 24 h. */
    @GetMapping("/{symbol}/stats")
    public SymbolStatsResponse stats(@PathVariable String symbol) {
        return reportingService.stats(symbol);
    }
}
