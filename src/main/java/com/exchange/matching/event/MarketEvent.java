package com.exchange.matching.event;

import com.exchange.matching.dto.OrderBookResponse;
import com.exchange.matching.dto.TradeResponse;

/**
 * Evenement de marche diffuse en temps reel aux clients WebSocket.
 *
 * Interface SCELLEE (sealed, Java 21): seuls les deux records listes dans
 * "permits" peuvent l'implementer. Le compilateur connait donc TOUS les cas
 * possibles, ce qui permet un switch exhaustif sans branche default (voir
 * MarketBroadcaster): si on ajoute un nouveau type d'evenement, chaque
 * switch qui l'oublie ne compile plus.
 */
public sealed interface MarketEvent permits MarketEvent.OrderBookUpdated, MarketEvent.TradeExecuted {

    /** Le carnet d'un symbole a change (nouvel ordre, annulation, execution). */
    record OrderBookUpdated(OrderBookResponse book) implements MarketEvent {}

    /** Une transaction vient d'etre executee. */
    record TradeExecuted(TradeResponse trade) implements MarketEvent {}
}
