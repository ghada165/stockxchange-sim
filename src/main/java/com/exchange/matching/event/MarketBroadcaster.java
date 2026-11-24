package com.exchange.matching.event;

import com.exchange.matching.dto.OrderBookResponse;
import com.exchange.matching.dto.TradeResponse;
import com.exchange.matching.event.MarketEvent.OrderBookUpdated;
import com.exchange.matching.event.MarketEvent.TradeExecuted;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Diffuse les evenements de marche sur les topics STOMP:
 *   /topic/market/{symbol}/orderbook
 *   /topic/market/{symbol}/trades
 *
 * Utilise un switch avec RECORD PATTERNS (Java 21): chaque case deconstruit
 * directement le record en ses composants, sans cast ni appel d'accesseur.
 * Pas de "default": MarketEvent est scelle, donc le switch est exhaustif.
 */
@Component
public class MarketBroadcaster {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void publish(MarketEvent event) {
        switch (event) {
            case OrderBookUpdated(OrderBookResponse book) ->
                messagingTemplate.convertAndSend(topic(book.symbol(), "orderbook"), book);
            case TradeExecuted(TradeResponse trade) ->
                messagingTemplate.convertAndSend(topic(trade.symbol(), "trades"), trade);
        }
    }

    private static String topic(String symbol, String channel) {
        return "/topic/market/" + symbol + "/" + channel;
    }
}
