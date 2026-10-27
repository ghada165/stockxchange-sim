package com.exchange.matching.controller;

import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.OrderBookResponse;
import com.exchange.matching.dto.TradeResponse;
import com.exchange.matching.service.TradingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Donnees de marche publiques (pas besoin d'etre identifie). */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final TradingService tradingService;

    public MarketController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /** GET /api/market/symbols : symboles cotes avec dernier prix et meilleurs prix. */
    @GetMapping("/symbols")
    public List<MarketSummaryResponse> symbols() {
        return tradingService.getMarketSummaries();
    }

    /** GET /api/market/{symbol}/orderbook : carnet d'ordres agrege par niveau de prix. */
    @GetMapping("/{symbol}/orderbook")
    public OrderBookResponse orderBook(@PathVariable String symbol) {
        return tradingService.getOrderBookSnapshot(symbol);
    }

    /** GET /api/market/{symbol}/trades : 50 dernieres transactions. */
    @GetMapping("/{symbol}/trades")
    public List<TradeResponse> recentTrades(@PathVariable String symbol) {
        return tradingService.getRecentTrades(symbol).stream()
            .map(TradeResponse::from)
            .toList();
    }
}
