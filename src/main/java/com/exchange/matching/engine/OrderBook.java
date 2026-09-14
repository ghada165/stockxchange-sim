package com.exchange.matching.engine;

import com.exchange.matching.model.Order;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Carnet d'ordres pour UN symbole.
 *
 * Structure: a chaque niveau de prix correspond une file FIFO d'ordres
 * (respect de la time-priority a prix egal).
 *
 * - bids (achats): tries du PLUS HAUT prix au plus bas -> le meilleur
 *   acheteur est le premier a etre servi.
 * - asks (ventes): tries du PLUS BAS prix au plus haut -> le meilleur
 *   vendeur est le premier a etre servi.
 *
 * THREAD-SAFETY: cette classe n'est PAS thread-safe par elle-meme. Elle est
 * concue pour etre accedee exclusivement a l'interieur d'une section critique
 * verrouillee par symbole (voir MatchingEngine, un ReentrantLock par symbole).
 * Ce choix (plutot que des collections concurrentes) est deliberement plus
 * simple et plus rapide: on ne verrouille jamais deux symboles differents en
 * meme temps, donc pas de contention inter-symboles, et pas de cout de
 * synchronisation fine inutile a l'interieur d'un meme symbole.
 */
public class OrderBook {

    private final String symbol;

    private final NavigableMap<BigDecimal, Deque<Order>> bids =
        new TreeMap<>(Comparator.reverseOrder());

    private final NavigableMap<BigDecimal, Deque<Order>> asks =
        new TreeMap<>(Comparator.naturalOrder());

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    private NavigableMap<BigDecimal, Deque<Order>> sideBook(com.exchange.matching.model.OrderSide side) {
        return side == com.exchange.matching.model.OrderSide.BUY ? bids : asks;
    }

    /** Ajoute un ordre LIMIT au carnet (queue FIFO du niveau de prix). */
    public void addOrder(Order order) {
        NavigableMap<BigDecimal, Deque<Order>> book = sideBook(order.getSide());
        book.computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>()).addLast(order);
    }

    /** Retire un ordre precis du carnet (annulation). Nettoie le niveau de prix si vide. */
    public boolean removeOrder(Order order) {
        NavigableMap<BigDecimal, Deque<Order>> book = sideBook(order.getSide());
        Deque<Order> level = book.get(order.getPrice());
        if (level == null) {
            return false;
        }
        boolean removed = level.remove(order);
        if (level.isEmpty()) {
            book.remove(order.getPrice());
        }
        return removed;
    }

    /** Meilleur prix d'achat (le plus haut), ou empty si le carnet cote achat est vide. */
    public Optional<BigDecimal> bestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
    }

    /** Meilleur prix de vente (le plus bas), ou empty si le carnet cote vente est vide. */
    public Optional<BigDecimal> bestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
    }

    /** Premiere entree (meilleur niveau) cote achat, avec sa file FIFO. */
    public Optional<Map.Entry<BigDecimal, Deque<Order>>> bestBidLevel() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstEntry());
    }

    /** Premiere entree (meilleur niveau) cote vente, avec sa file FIFO. */
    public Optional<Map.Entry<BigDecimal, Deque<Order>>> bestAskLevel() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstEntry());
    }

    /** Supprime un niveau de prix du cote achat s'il est vide. */
    public void pruneIfEmpty(com.exchange.matching.model.OrderSide side, BigDecimal price) {
        NavigableMap<BigDecimal, Deque<Order>> book = sideBook(side);
        Deque<Order> level = book.get(price);
        if (level != null && level.isEmpty()) {
            book.remove(price);
        }
    }

    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    /**
     * Snapshot en lecture seule du carnet, pour diffusion (WebSocket) ou debug.
     * Ne pas exposer les Deque internes directement pour eviter toute mutation externe.
     */
    public NavigableMap<BigDecimal, Deque<Order>> viewBids() {
        return bids;
    }

    public NavigableMap<BigDecimal, Deque<Order>> viewAsks() {
        return asks;
    }
}
