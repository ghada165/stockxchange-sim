package com.exchange.matching.engine;

import com.exchange.matching.exception.OrderNotFoundException;
import com.exchange.matching.model.Order;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Moteur de matching d'ordres, applique la regle "price-time priority":
 * a prix egal, le premier ordre arrive est le premier servi.
 *
 * CONCURRENCE:
 * Un ReentrantLock est cree par symbole (pas un lock global). Deux ordres sur
 * des symboles differents peuvent donc etre traites en parallele sans jamais
 * se bloquer mutuellement; deux ordres sur le MEME symbole sont serialises
 * pour garantir l'integrite du carnet. C'est le point critique verifie par
 * {@code MatchingEngineConcurrencyTest}.
 *
 * On utilise ReentrantLock plutot que synchronized: c'est le choix
 * recommande avec les virtual threads de Java 21 (un bloc synchronized
 * peut "epingler" le virtual thread sur son thread porteur).
 *
 * PRIX D'EXECUTION: un trade s'execute toujours au prix de l'ordre PASSIF
 * (celui deja dans le carnet), jamais au prix de l'ordre agressif.
 *
 * AUTO-TRADE: un utilisateur ne peut jamais executer contre son propre
 * ordre. Si l'ordre entrant rencontre un ordre du meme utilisateur en tete
 * du carnet, le reliquat de l'ordre entrant est annule ("cancel newest").
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
     * de type LIMIT, il est place dans le carnet en attente. Le reliquat d'un
     * ordre MARKET est annule: un MARKET ne reste jamais dans le carnet.
     *
     * @return les trades generes (peut etre vide) et l'etat de l'ordre apres matching
     */
    public MatchResult submitOrder(Order order) {
        return withLock(order.getSymbol(), () -> {
            order.setSequence(sequenceGenerator.incrementAndGet());
            OrderBook book = orderBooks.computeIfAbsent(order.getSymbol(), OrderBook::new);

            List<Trade> trades = match(order, book);

            if (order.isActive() && order.getRemainingQuantity().signum() > 0) {
                if (order.getType() == OrderType.LIMIT) {
                    book.addOrder(order);
                    activeOrdersById.put(order.getId(), order);
                } else {
                    order.cancel();
                    log.debug("Ordre MARKET {} annule pour le reliquat non-executable ({})",
                        order.getId(), order.getRemainingQuantity());
                }
            }
            return new MatchResult(List.copyOf(trades), order.getStatus(), order.getRemainingQuantity());
        });
    }

    /**
     * Annule un ordre actif (OPEN ou PARTIALLY_FILLED) et le renvoie.
     * La quantite restante de l'ordre renvoye est la valeur de reference
     * pour liberer la reservation: elle est lue sous le verrou du symbole,
     * donc aucun fill concurrent ne peut la modifier entre-temps.
     *
     * @throws OrderNotFoundException si l'ordre n'existe pas ou n'est plus actif
     */
    public Order cancelOrder(String symbol, String orderId) {
        return withLock(symbol, () -> {
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
            return order;
        });
    }

    /**
     * Calcule le prix "pire cas" a atteindre pour executer {@code quantity}
     * sur le carnet oppose: le prix du dernier niveau qu'il faudrait consommer.
     * Si la liquidite est insuffisante, renvoie le prix du dernier niveau
     * disponible (le reliquat sera annule). Empty si le carnet oppose est vide.
     *
     * Sert de plafond (BUY) / plancher (SELL) a un ordre MARKET: voir
     * {@link Order#marketWithCap}.
     */
    public Optional<BigDecimal> estimatePriceCap(String symbol, OrderSide side, BigDecimal quantity) {
        return readBook(symbol, book -> {
            var opposite = side == OrderSide.BUY ? book.viewAsks() : book.viewBids();
            BigDecimal accumulated = BigDecimal.ZERO;
            BigDecimal worstPrice = null;
            for (Map.Entry<BigDecimal, Deque<Order>> level : opposite.entrySet()) {
                worstPrice = level.getKey();
                for (Order resting : level.getValue()) {
                    accumulated = accumulated.add(resting.getRemainingQuantity());
                }
                if (accumulated.compareTo(quantity) >= 0) {
                    break;
                }
            }
            return Optional.ofNullable(worstPrice);
        });
    }

    /**
     * Lit le carnet d'un symbole SOUS SON VERROU. A utiliser pour tout
     * snapshot (API REST, WebSocket): OrderBook n'est pas thread-safe, une
     * lecture sans verrou pendant un matching pourrait lever une
     * ConcurrentModificationException ou lire un etat incoherent.
     */
    public <T> T readBook(String symbol, Function<OrderBook, T> reader) {
        return withLock(symbol, () -> reader.apply(orderBooks.computeIfAbsent(symbol, OrderBook::new)));
    }

    /**
     * Reinsere un ordre RESTANT (deja actif avant un redemarrage) directement
     * dans le carnet, sans passer par le matching ni reassigner de sequence.
     * Reserve exclusivement au rechargement au demarrage (voir OrderBookLoader).
     */
    public void restoreOrder(Order order) {
        withLock(order.getSymbol(), () -> {
            OrderBook book = orderBooks.computeIfAbsent(order.getSymbol(), OrderBook::new);
            book.addOrder(order);
            activeOrdersById.put(order.getId(), order);
            return null;
        });
    }

    /**
     * Avance le generateur de sequence pour qu'il ne re-emette jamais un
     * numero deja utilise par un ordre recharge depuis la persistance.
     */
    public void fastForwardSequence(long atLeast) {
        sequenceGenerator.updateAndGet(current -> Math.max(current, atLeast));
    }

    /**
     * Acces direct au carnet, SANS verrou. Reserve aux tests mono-thread;
     * en production, passer par {@link #readBook}.
     */
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
                break; // le meilleur prix oppose ne satisfait plus la limite (ou le plafond)
            }

            Order resting = queue.peekFirst();
            if (resting == null) {
                book.pruneIfEmpty(oppositeSide(incoming.getSide()), levelPrice);
                continue;
            }

            if (resting.getUserId().equals(incoming.getUserId())) {
                // Prevention de l'auto-trade: on annule le reliquat de l'ordre entrant.
                log.info("Auto-trade evite: ordre {} annule (contrepartie {} du meme utilisateur)",
                    incoming.getId(), resting.getId());
                incoming.cancel();
                break;
            }

            BigDecimal matchedQty = incoming.getRemainingQuantity().min(resting.getRemainingQuantity());

            Order buyOrder = incoming.getSide() == OrderSide.BUY ? incoming : resting;
            Order sellOrder = incoming.getSide() == OrderSide.SELL ? incoming : resting;

            // Convention: execution au prix de l'ordre PASSIF (deja dans le carnet)
            Trade trade = Trade.execute(incoming.getSymbol(), buyOrder, sellOrder, levelPrice, matchedQty);
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
        }

        if (incoming.getStatus() == OrderStatus.FILLED) {
            activeOrdersById.remove(incoming.getId());
        }
        return trades;
    }

    /**
     * Determine si l'ordre entrant peut executer contre un niveau de prix oppose.
     * Un LIMIT (ou un MARKET protege par un plafond) achat croise si sa limite
     * est >= au prix de vente propose; une vente croise si sa limite est <=
     * au prix d'achat propose. Un MARKET sans plafond croise toujours.
     */
    private boolean pricesCross(Order incoming, BigDecimal oppositeLevelPrice) {
        BigDecimal limit = incoming.getType() == OrderType.LIMIT ? incoming.getPrice() : incoming.getPriceCap();
        if (limit == null) {
            return true;
        }
        return incoming.getSide() == OrderSide.BUY
            ? limit.compareTo(oppositeLevelPrice) >= 0
            : limit.compareTo(oppositeLevelPrice) <= 0;
    }

    private OrderSide oppositeSide(OrderSide side) {
        return side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;
    }

    private <T> T withLock(String symbol, Supplier<T> action) {
        Lock lock = symbolLocks.computeIfAbsent(symbol, s -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
