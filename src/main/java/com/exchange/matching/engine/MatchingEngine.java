package com.exchange.matching.engine;

import com.exchange.matching.exception.OrderNotFoundException;
import com.exchange.matching.model.Order;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Moteur de matching d'ordres, applique la regle "price-time priority":
 * a prix egal, le premier ordre arrive est le premier servi.
 *
 * CONCURRENCE:
 * Un ReentrantLock est cree par symbole (pas un lock global). Deux ordres sur
 * des symboles differents peuvent donc etre traites en parallele sans jamais
 * se bloquer mutuellement; deux ordres sur le MEME symbole sont serialises
 * pour garantir l'integrite du carnet (pas de lecture-modification concurrente
 * du meme OrderBook). C'est le point critique verifie par
 * {@code MatchingEngineConcurrencyTest}.
 *
 * PRIX D'EXECUTION: convention standard des exchanges - un trade s'execute
 * toujours au prix de l'ordre PASSIF (celui deja dans le carnet), jamais au
 * prix de l'ordre agressif qui arrive. Ca evite qu'un acheteur agressif a un
 * prix tres haut ne "paie" plus que necessaire: il paie le prix du vendeur
 * deja present.
 */
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<String, Lock> symbolLocks = new ConcurrentHashMap<>();
    private final Map<String, Order> activeOrdersById = new ConcurrentHashMap<>();
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    /**
     * Soumet un ordre au moteur. Tente de le matcher immediatement contre le
     * carnet oppose; s'il reste une quantite non executee et que l'ordre est
     * de type LIMIT, il est place dans le carnet en attente. Un ordre MARKET
     * dont il reste une quantite non executee (liquidite insuffisante) est
     * automatiquement annule pour le reliquat: un MARKET ne reste jamais dans
     * le carnet.
     *
     * @return la liste des trades generes (peut etre vide)
     */
    public List<Trade> submitOrder(Order order) {
        Lock lock = symbolLocks.computeIfAbsent(order.getSymbol(), s -> new ReentrantLock());
        lock.lock();
        try {
            order.setSequence(sequenceGenerator.incrementAndGet());
            OrderBook book = orderBooks.computeIfAbsent(order.getSymbol(), OrderBook::new);

            List<Trade> trades = match(order, book);

            if (order.isActive() && order.getRemainingQuantity().signum() > 0) {
                if (order.getType() == OrderType.LIMIT) {
                    book.addOrder(order);
                    activeOrdersById.put(order.getId(), order);
                } else {
                    // MARKET partiellement (ou pas du tout) execute: pas de reliquat en carnet.
                    order.cancel();
                    log.debug("Ordre MARKET {} annule pour le reliquat non-executable ({})",
                        order.getId(), order.getRemainingQuantity());
                }
            } else if (order.getStatus() == com.exchange.matching.model.OrderStatus.FILLED) {
                activeOrdersById.remove(order.getId());
            }

            return trades;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Annule un ordre actif (OPEN ou PARTIALLY_FILLED).
     *
     * @throws OrderNotFoundException si l'ordre n'existe pas ou n'est plus actif
     */
    public void cancelOrder(String symbol, String orderId) {
        Lock lock = symbolLocks.computeIfAbsent(symbol, s -> new ReentrantLock());
        lock.lock();
        try {
            Order order = activeOrdersById.get(orderId);
            if (order == null || !order.getSymbol().equals(symbol)) {
                throw new OrderNotFoundException(orderId);
            }
            OrderBook book = orderBooks.get(symbol);
            if (book != null) {
                book.removeOrder(order);
            }
            order.cancel();
            activeOrdersById.remove(orderId);
        } finally {
            lock.unlock();
        }
    }

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, OrderBook::new);
    }

    // ------------------------------------------------------------------
    // Algorithme de matching
    // ------------------------------------------------------------------

    private List<Trade> match(Order incoming, OrderBook book) {
        List<Trade> trades = new ArrayList<>();

        while (incoming.isActive() && incoming.getRemainingQuantity().signum() > 0) {
            Optional<Map.Entry<BigDecimal, Deque<Order>>> bestLevel =
                incoming.getSide() == OrderSide.BUY ? book.bestAskLevel() : book.bestBidLevel();

            if (bestLevel.isEmpty()) {
                break; // plus de contrepartie disponible
            }

            BigDecimal levelPrice = bestLevel.get().getKey();
            Deque<Order> queue = bestLevel.get().getValue();

            if (!pricesCross(incoming, levelPrice)) {
                break; // meilleur prix oppose ne satisfait plus la limite de l'ordre entrant
            }

            Order resting = queue.peekFirst();
            if (resting == null) {
                // Niveau vide (ne devrait pas arriver, nettoyage defensif)
                book.pruneIfEmpty(oppositeSide(incoming.getSide()), levelPrice);
                continue;
            }

            BigDecimal matchedQty = incoming.getRemainingQuantity()
                .min(resting.getRemainingQuantity());

            // Convention: execution au prix de l'ordre PASSIF (deja dans le carnet)
            BigDecimal executionPrice = levelPrice;

            String buyOrderId = incoming.getSide() == OrderSide.BUY ? incoming.getId() : resting.getId();
            String sellOrderId = incoming.getSide() == OrderSide.SELL ? incoming.getId() : resting.getId();

            Trade trade = new Trade(incoming.getSymbol(), buyOrderId, sellOrderId, executionPrice, matchedQty);
            trades.add(trade);

            incoming.reduceRemaining(matchedQty);
            resting.reduceRemaining(matchedQty);

            log.info("Trade execute: {}", trade);

            if (resting.getRemainingQuantity().signum() == 0) {
                queue.pollFirst();
                activeOrdersById.remove(resting.getId());
                if (queue.isEmpty()) {
                    book.pruneIfEmpty(oppositeSide(incoming.getSide()), levelPrice);
                }
            }
            // si resting a encore du volume restant, il reste en tete de file
            // (priorite time preservee pour le prochain passage de boucle)
        }

        return trades;
    }

    /**
     * Determine si l'ordre entrant peut executer contre un niveau de prix oppose.
     * Un ordre MARKET croise toujours. Un ordre LIMIT achat croise si son prix
     * limite est >= au prix de vente propose; un LIMIT vente croise si son prix
     * limite est <= au prix d'achat propose.
     */
    private boolean pricesCross(Order incoming, BigDecimal oppositeLevelPrice) {
        if (incoming.getType() == OrderType.MARKET) {
            return true;
        }
        return incoming.getSide() == OrderSide.BUY
            ? incoming.getPrice().compareTo(oppositeLevelPrice) >= 0
            : incoming.getPrice().compareTo(oppositeLevelPrice) <= 0;
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }
}
