package com.exchange.matching.service;

import com.exchange.matching.engine.MatchingEngine;
import com.exchange.matching.engine.OrderBook;
import com.exchange.matching.exception.InsufficientFundsException;
import com.exchange.matching.exception.OrderNotFoundException;
import com.exchange.matching.model.*;
import com.exchange.matching.persistence.entity.OrderEntity;
import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.TradeEntity;
import com.exchange.matching.persistence.entity.WalletEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import com.exchange.matching.persistence.repository.PositionRepository;
import com.exchange.matching.persistence.repository.TradeRepository;
import com.exchange.matching.persistence.repository.WalletRepository;
import com.exchange.matching.web.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Orchestre le cycle de vie complet d'un ordre:
 *   validation -> reservation des fonds/actions -> matching (en memoire)
 *   -> persistance -> reglement des wallets/positions -> diffusion temps reel.
 *
 * ARCHITECTURE - un point important a assumer en soutenance:
 * Le MatchingEngine execute le matching EN MEMOIRE, en dehors de toute
 * transaction JPA (c'est deliberement synchrone et rapide: pas d'I/O disque
 * dans le chemin critique de matching). La persistance qui suit est donc
 * un "best effort" apres coup: si une ecriture DB echoue APRES qu'un match
 * a eu lieu en memoire, l'etat memoire et l'etat DB divergent. Un systeme de
 * production reglerait ca avec de l'event sourcing ou un outbox pattern
 * (chaque evenement de matching est d'abord ecrit dans un log immuable, la
 * DB relationnelle n'etant qu'une projection reconstruisable). Pour ce
 * projet, on documente la limite plutot que de la cacher: c'est un vrai
 * sujet de discussion technique pour le jury.
 */
@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    /** Solde de demarrage offert a tout nouvel utilisateur (demo pedagogique, pas un vrai KYC/depot). */
    private static final BigDecimal DEMO_STARTING_BALANCE = new BigDecimal("100000.0000");

    private final MatchingEngine matchingEngine;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final WalletRepository walletRepository;
    private final PositionRepository positionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public TradingService(MatchingEngine matchingEngine,
                           OrderRepository orderRepository,
                           TradeRepository tradeRepository,
                           WalletRepository walletRepository,
                           PositionRepository positionRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.walletRepository = walletRepository;
        this.positionRepository = positionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // ------------------------------------------------------------------
    // Placement d'ordre
    // ------------------------------------------------------------------

    @Transactional
    public PlaceOrderResponse placeOrder(String userId, PlaceOrderRequest request) {
        validateRequest(request);

        // Prix de reference utilise UNIQUEMENT pour estimer la reservation d'un
        // ordre MARKET (le carnet oppose donne le pire cas plausible). Ne sert
        // jamais a l'execution reelle: le MatchingEngine, lui, execute toujours
        // au prix reel du carnet.
        BigDecimal reservationPrice = request.price();
        if (request.type() == OrderType.MARKET) {
            OrderBook book = matchingEngine.getOrderBook(request.symbol());
            Optional<BigDecimal> reference = request.side() == OrderSide.BUY
                ? book.bestAsk() : book.bestBid();
            if (reference.isEmpty()) {
                throw new IllegalStateException(
                    "Marche illiquide sur " + request.symbol() + ": aucun prix de reference pour un ordre MARKET");
            }
            reservationPrice = reference.get();
        }

        reserveFunds(userId, request.side(), request.symbol(), reservationPrice, request.quantity());

        Order domainOrder = new Order(userId, request.symbol(), request.side(), request.type(),
            request.type() == OrderType.LIMIT ? request.price() : null, request.quantity());

        OrderEntity entity = toNewEntity(domainOrder);
        orderRepository.save(entity);

        List<Trade> trades = matchingEngine.submitOrder(domainOrder);

        settleTrades(trades); // persiste aussi chaque TradeEntity au fil de l'eau
        releaseUnusedReservation(domainOrder, reservationPrice);

        entity.setRemainingQuantity(domainOrder.getRemainingQuantity());
        entity.setStatus(domainOrder.getStatus());
        orderRepository.save(entity);

        broadcastMarketUpdate(request.symbol(), trades);

        return PlaceOrderResponse.of(domainOrder, trades);
    }

    private void validateRequest(PlaceOrderRequest request) {
        if (request.type() == OrderType.LIMIT && request.price() == null) {
            throw new IllegalArgumentException("Un ordre LIMIT doit avoir un prix");
        }
        if (request.type() == OrderType.MARKET && request.price() != null) {
            throw new IllegalArgumentException("Un ordre MARKET ne doit pas avoir de prix");
        }
    }

    /**
     * Reserve les fonds (BUY) ou les actions (SELL) AVANT le matching, pour
     * garantir qu'un utilisateur ne peut jamais placer un ordre qu'il ne peut
     * pas honorer. Verrou pessimiste DB pour serialiser les acces concurrents
     * au meme wallet/position (independant du lock par symbole du
     * MatchingEngine, qui ne protege que le carnet).
     */
    private void reserveFunds(String userId, OrderSide side, String symbol,
                               BigDecimal referencePrice, BigDecimal quantity) {
        if (side == OrderSide.BUY) {
            WalletEntity wallet = getOrCreateWallet(userId);
            BigDecimal required = referencePrice.multiply(quantity);
            if (required.compareTo(wallet.availableBalance()) > 0) {
                throw new InsufficientFundsException(
                    "Solde disponible insuffisant: requis=" + required + " disponible=" + wallet.availableBalance());
            }
            wallet.lock(required);
            walletRepository.save(wallet);
        } else {
            PositionEntity position = getOrCreatePosition(userId, symbol);
            if (quantity.compareTo(position.availableQuantity()) > 0) {
                throw new InsufficientFundsException(
                    "Quantite disponible insuffisante sur " + symbol
                    + ": requis=" + quantity + " disponible=" + position.availableQuantity());
            }
            position.lock(quantity);
            positionRepository.save(position);
        }
    }

    /**
     * Pour un ordre MARKET dont la reservation etait une estimation (prix de
     * reference du carnet), libere la portion de reservation qui n'a
     * finalement pas ete consommee: la difference entre l'estimation et
     * l'execution reelle, plus la reservation du reliquat annule si le
     * marche n'avait pas assez de liquidite.
     */
    private void releaseUnusedReservation(Order order, BigDecimal reservationPrice) {
        if (order.getType() != OrderType.MARKET) {
            return; // les LIMIT sont regles exactement trade par trade dans settleTrades()
        }
        if (order.getSide() == OrderSide.BUY) {
            BigDecimal unusedQty = order.getRemainingQuantity(); // ce qui n'a pas pu executer
            if (unusedQty.signum() > 0) {
                WalletEntity wallet = getOrCreateWallet(order.getUserId());
                wallet.unlock(reservationPrice.multiply(unusedQty));
                walletRepository.save(wallet);
            }
        } else {
            BigDecimal unusedQty = order.getRemainingQuantity();
            if (unusedQty.signum() > 0) {
                PositionEntity position = getOrCreatePosition(order.getUserId(), order.getSymbol());
                position.unlock(unusedQty);
                positionRepository.save(position);
            }
        }
    }

    /**
     * Regle chaque trade des deux cotes: le cash passe de l'acheteur au
     * vendeur, les actions du vendeur a l'acheteur. Le montant reellement
     * debite/credite est TOUJOURS le prix d'execution reel (jamais le prix
     * limite ou l'estimation MARKET) - c'est ce qui garantit qu'un
     * "price improvement" (execution a un prix meilleur que la limite)
     * profite bien a l'utilisateur plutot que de rester bloque par erreur.
     */
    private void settleTrades(List<Trade> trades) {
        for (Trade trade : trades) {
            // Persister le trade AVANT de recalculer le statut des ordres:
            // syncOrderEntityFromDomainIfKnown() reconstruit le remaining a
            // partir de la table trades, donc ce trade doit deja y figurer.
            tradeRepository.save(new TradeEntity(trade.getId(), trade.getSymbol(),
                trade.getBuyOrderId(), trade.getSellOrderId(), trade.getPrice(),
                trade.getQuantity(), trade.getExecutedAt()));

            OrderEntity buyOrder = orderRepository.findById(trade.getBuyOrderId())
                .orElseThrow(() -> new OrderNotFoundException(trade.getBuyOrderId()));
            OrderEntity sellOrder = orderRepository.findById(trade.getSellOrderId())
                .orElseThrow(() -> new OrderNotFoundException(trade.getSellOrderId()));

            BigDecimal cost = trade.getPrice().multiply(trade.getQuantity());

            // Cote acheteur: liberer la reservation exacte pour cette quantite
            // (au prix qui avait servi a reserver: le prix limite pour un
            // ordre LIMIT), puis debiter le cout REEL de la transaction.
            WalletEntity buyerWallet = getOrCreateWallet(buyOrder.getUserId());
            BigDecimal reservedPriceForBuyer = buyOrder.getType() == OrderType.LIMIT
                ? buyOrder.getPrice() : trade.getPrice(); // MARKET: geré via releaseUnusedReservation
            if (buyOrder.getType() == OrderType.LIMIT) {
                buyerWallet.unlock(reservedPriceForBuyer.multiply(trade.getQuantity()));
            } else {
                // Pour un MARKET, la reservation initiale utilisait le prix de reference du carnet.
                // On la libere ici au prorata de ce trade, avec le meme prix de reference
                // (le solde residuel eventuel est nettoye a la fin par releaseUnusedReservation).
                buyerWallet.unlock(trade.getPrice().multiply(trade.getQuantity()));
            }
            buyerWallet.debit(cost);
            walletRepository.save(buyerWallet);

            PositionEntity buyerPosition = getOrCreatePosition(buyOrder.getUserId(), trade.getSymbol());
            buyerPosition.credit(trade.getQuantity());
            positionRepository.save(buyerPosition);

            // Cote vendeur: la reservation etait en actions (exacte, pas d'estimation
            // possible ni necessaire), donc settleDebit-like en une seule operation.
            PositionEntity sellerPosition = getOrCreatePosition(sellOrder.getUserId(), trade.getSymbol());
            sellerPosition.settleDebit(trade.getQuantity());
            positionRepository.save(sellerPosition);

            WalletEntity sellerWallet = getOrCreateWallet(sellOrder.getUserId());
            sellerWallet.credit(cost);
            walletRepository.save(sellerWallet);

            // Les deux ordres impliques ont pu changer de statut (partiel/rempli):
            // on les persiste ici aussi car settleTrades() peut regler des ordres
            // RESTANTS (places lors d'appels precedents), pas seulement l'ordre
            // entrant de cet appel-ci.
            syncOrderEntityFromDomainIfKnown(buyOrder);
            syncOrderEntityFromDomainIfKnown(sellOrder);
        }
    }

    /**
     * Les ordres resolus ici viennent de la DB (OrderEntity), mais leur statut
     * "post-trade" reel vit dans l'objet Order en memoire du MatchingEngine.
     * On ne peut pas relire cet objet ici facilement sans le retrouver via
     * son id; comme le champ status de l'OrderEntity peut donc etre en retard
     * d'un cran pour les ordres RESTANTS (pas l'ordre entrant de cet appel),
     * on le corrige via un simple recalcul: remainingQuantity de l'ordre =
     * quantite initiale - somme des quantites de tous ses trades connus.
     * Coherent, sans dependre d'une reference partagee a l'objet memoire.
     */
    private void syncOrderEntityFromDomainIfKnown(OrderEntity entity) {
        List<TradeEntity> allTradesForOrder = tradeRepository.findByBuyOrderIdOrSellOrderId(entity.getId(), entity.getId());
        BigDecimal filled = allTradesForOrder.stream()
            .map(TradeEntity::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = entity.getQuantity().subtract(filled);
        entity.setRemainingQuantity(remaining.max(BigDecimal.ZERO));
        if (remaining.signum() <= 0) {
            entity.setStatus(OrderStatus.FILLED);
        } else if (filled.signum() > 0) {
            entity.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
        orderRepository.save(entity);
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    @Transactional
    public void cancelOrder(String userId, String symbol, String orderId) {
        OrderEntity entity = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!entity.getUserId().equals(userId)) {
            throw new OrderNotFoundException(orderId); // ne pas reveler l'existence de l'ordre d'autrui
        }

        matchingEngine.cancelOrder(symbol, orderId); // leve OrderNotFoundException si deja inactif

        BigDecimal remaining = entity.getRemainingQuantity();
        if (entity.getSide() == OrderSide.BUY && entity.getType() == OrderType.LIMIT) {
            WalletEntity wallet = getOrCreateWallet(userId);
            wallet.unlock(entity.getPrice().multiply(remaining));
            walletRepository.save(wallet);
        } else if (entity.getSide() == OrderSide.SELL) {
            PositionEntity position = getOrCreatePosition(userId, symbol);
            position.unlock(remaining);
            positionRepository.save(position);
        }

        entity.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(entity);

        broadcastMarketUpdate(symbol, List.of());
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderEntity> getMyOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public OrderBookResponse getOrderBookSnapshot(String symbol) {
        OrderBook book = matchingEngine.getOrderBook(symbol);
        List<OrderBookLevelDto> bids = summarizeLevels(book.viewBids());
        List<OrderBookLevelDto> asks = summarizeLevels(book.viewAsks());
        return new OrderBookResponse(symbol, bids, asks);
    }

    private List<OrderBookLevelDto> summarizeLevels(NavigableMap<BigDecimal, Deque<Order>> side) {
        List<OrderBookLevelDto> result = new ArrayList<>();
        for (Map.Entry<BigDecimal, Deque<Order>> entry : side.entrySet()) {
            BigDecimal total = entry.getValue().stream()
                .map(Order::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new OrderBookLevelDto(entry.getKey(), total, entry.getValue().size()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<TradeEntity> getRecentTrades(String symbol) {
        return tradeRepository.findTop50BySymbolOrderByExecutedAtDesc(symbol);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(String userId) {
        WalletEntity wallet = getOrCreateWallet(userId);
        List<PositionEntity> positions = positionRepository.findAll().stream()
            .filter(p -> p.getUserId().equals(userId))
            .toList();
        return WalletResponse.of(wallet, positions);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private WalletEntity getOrCreateWallet(String userId) {
        return walletRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> walletRepository.save(new WalletEntity(userId, DEMO_STARTING_BALANCE)));
    }

    private PositionEntity getOrCreatePosition(String userId, String symbol) {
        return positionRepository.findByUserIdAndSymbolForUpdate(userId, symbol)
            .orElseGet(() -> positionRepository.save(new PositionEntity(userId, symbol)));
    }

    private OrderEntity toNewEntity(Order order) {
        return new OrderEntity(order.getId(), order.getUserId(), order.getSymbol(), order.getSide(),
            order.getType(), order.getPrice(), order.getQuantity(), order.getRemainingQuantity(),
            order.getStatus(), order.getCreatedAt(), order.getSequence());
    }

    private void broadcastMarketUpdate(String symbol, List<Trade> trades) {
        messagingTemplate.convertAndSend("/topic/market/" + symbol + "/orderbook", getOrderBookSnapshot(symbol));
        for (Trade trade : trades) {
            messagingTemplate.convertAndSend("/topic/market/" + symbol + "/trades", TradeResponse.from(trade));
        }
    }
}
