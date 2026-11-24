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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Module Marche: donnees publiques, accessibles aux visiteurs sans connexion. */
@RestController
@RequestMapping("/api/market")
@Tag(name = "Marche", description = "Donnees publiques, accessibles sans connexion")
public class MarketController {

    @Autowired
    private TradingService tradingService;
    @Autowired
    private ReportingService reportingService;
    @Autowired
    private MarketHoursService marketHours;

    /** GET /api/market/status : marche ouvert ou ferme, et ses horaires. */
    @GetMapping("/status")
    @Operation(summary = "Etat du marche", description = "Ouvert ou ferme, horaires et jours d'ouverture.")
    public MarketStatusResponse status() {
        return marketHours.status();
    }

    /** GET /api/market/symbols : symboles cotes avec statut, dernier prix et meilleurs prix. */
    @GetMapping("/symbols")
    @Operation(summary = "Symboles cotes", description = "Statut, dernier prix et meilleurs prix achat / vente.")
    public List<MarketSummaryResponse> symbols() {
        return tradingService.getMarketSummaries();
    }

    /** GET /api/market/{symbol}/orderbook : carnet d'ordres agrege par niveau de prix. */
    @GetMapping("/{symbol}/orderbook")
    @Operation(summary = "Carnet d'ordres", description = "Agrege par niveau de prix.")
    public OrderBookResponse orderBook(@Parameter(description = "Code du symbole", example = "AAPL") @PathVariable String symbol) {
        return tradingService.getOrderBookSnapshot(symbol);
    }

    /** GET /api/market/{symbol}/trades : 50 dernieres transactions (historique par symbole). */
    @GetMapping("/{symbol}/trades")
    @Operation(summary = "Dernieres transactions", description = "50 dernieres executions sur le symbole.")
    public List<TradeResponse> recentTrades(@Parameter(description = "Code du symbole", example = "AAPL") @PathVariable String symbol) {
        return tradingService.getRecentTrades(symbol).stream()
            .map(TradeResponse::from)
            .toList();
    }

    /** GET /api/market/{symbol}/candles?interval=1m : historique de prix (1m, 5m, 15m, 1h). */
    @GetMapping("/{symbol}/candles")
    @Operation(summary = "Historique de prix", description = "Chandeliers OHLC: 1m, 5m, 15m ou 1h.")
    public List<CandleResponse> candles(@Parameter(description = "Code du symbole", example = "AAPL") @PathVariable String symbol,
                                        @Parameter(description = "1m, 5m, 15m ou 1h") @RequestParam(defaultValue = "1m") String interval) {
        return reportingService.candles(symbol, interval);
    }

    /** GET /api/market/{symbol}/stats : volume, prix moyen, plus haut, plus bas sur 24 h. */
    @GetMapping("/{symbol}/stats")
    @Operation(summary = "Statistiques 24 h", description = "Volume, prix moyen pondere, plus haut, plus bas, variation.")
    public SymbolStatsResponse stats(@Parameter(description = "Code du symbole", example = "AAPL") @PathVariable String symbol) {
        return reportingService.stats(symbol);
    }
}
