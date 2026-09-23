package com.exchange.matching.web;

import com.exchange.matching.persistence.entity.TradeEntity;
import com.exchange.matching.service.TradingService;
import com.exchange.matching.web.dto.OrderBookResponse;
import com.exchange.matching.web.dto.TradeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final TradingService tradingService;

    public MarketController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @GetMapping("/{symbol}/orderbook")
    public OrderBookResponse orderBook(@PathVariable String symbol) {
        return tradingService.getOrderBookSnapshot(symbol);
    }

    @GetMapping("/{symbol}/trades")
    public List<TradeResponse> recentTrades(@PathVariable String symbol) {
        return tradingService.getRecentTrades(symbol).stream()
            .map(this::toResponse)
            .toList();
    }

    private TradeResponse toResponse(TradeEntity entity) {
        TradeResponse r = new TradeResponse();
        r.id = entity.getId();
        r.symbol = entity.getSymbol();
        r.price = entity.getPrice();
        r.quantity = entity.getQuantity();
        r.executedAt = entity.getExecutedAt();
        return r;
    }
}
