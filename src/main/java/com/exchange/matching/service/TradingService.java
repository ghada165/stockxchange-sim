package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.dto.*;
import com.exchange.matching.engine.MatchResult;
import com.exchange.matching.engine.MatchingEngine;
import com.exchange.matching.engine.OrderBook;
import com.exchange.matching.event.MarketBroadcaster;
import com.exchange.matching.event.MarketEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * Orchestre le cycle de vie complet d'un ordre.
 *
 * PLACEMENT EN 3 ETAPES:
 *   1. Transaction courte: creation du compte si besoin, reservation des
 *      fonds (BUY) ou des actions (SELL), enregistrement de l'ordre OPEN.
 *      Une seule ligne de compte est verrouillee.
 *   2. Matching EN MEMOIRE dans le MatchingEngine (verrou par symbole,
 *      aucune I/O disque dans le chemin critique).
 *   3. Transaction de reglement: tous les comptes concernes sont verrouilles
 *      dans un ORDRE GLOBAL FIXE (wallets tries par userId, puis positions
 *      triees par userId, puis ordres tries par id). Deux reglements
 *      concurrents prennent donc toujours les verrous dans le meme ordre:
 *      aucun interblocage (deadlock) possible en base.
 *   Puis diffusion WebSocket, APRES le commit.
 *
 * LIMITE ASSUMEE: le matching (etape 2) n'est pas dans une transaction DB.
 * Si le reglement (etape 3) echouait, l'etat memoire et la base
 * divergeraient. Un systeme de production reglerait ca avec de l'event
 * sourcing ou un outbox pattern (journal immuable des evenements de
 * matching, la base relationnelle n'etant qu'une projection reconstruisable).
 */
@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final MatchingEngine matchingEngine;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final WalletRepository walletRepository;
    private final PositionRepository positionRepository;
    private final MarketBroadcaster broadcaster;
    private final ExchangeProperties properties;
    private final TransactionTemplate transactionTemplate;

    public TradingService(MatchingEngine matchingEngine,
                          OrderRepository orderRepository,
                          TradeRepository tradeRepository,
                          WalletRepository walletRepository,
                          PositionRepository positionRepository,
                          MarketBroadcaster broadcaster,
                          ExchangeProperties properties,
                          TransactionTemplate transactionTemplate) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.walletRepository = walletRepository;
        this.positionRepository = positionRepository;
        this.broadcaster = broadcaster;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    // ------------------------------------------------------------------
    // Placement d'ordre
    // ------------------------------------------------------------------

    public PlaceOrderResponse placeOrder(String userId, PlaceOrderRequest request) {
        validateRequest(request);

        Order order = buildOrder(userId, request);
        BigDecimal reservationPrice = order.getType() == OrderType.LIMIT ? order.getPrice() : order.getPriceCap();

        // 1. Reservation + enregistrement de l'ordre (transaction courte)
        transactionTemplate.executeWithoutResult(status -> {
            ensureAccount(userId);
            reserve(userId, order.getSide(), order.getSymbol(), reservationPrice, order.getQuantity());
            orderRepository.save(toNewEntity(order, reservationPrice));
        });

        // 2. Matching en memoire
        MatchResult result = matchingEngine.submitOrder(order);

        // 3. Reglement des trades et statut final
        transactionTemplate.executeWithoutResult(status -> settle(order, result));

        // 4. Diffusion temps reel, seulement une fois tout commite
        broadcast(order.getSymbol(), result.trades());

        return PlaceOrderResponse.of(order, result);
    }

    /**
     * Coherence prix / type d'ordre, ecrite avec un switch sur RECORD PATTERN:
     * la requete est deconstruite en ses composants, et la garde "when"
     * exprime la regle metier directement sur ces composants.
     */
    private void validateRequest(PlaceOrderRequest request) {
        switch (request) {
            case PlaceOrderRequest(String symbol, OrderSide side, OrderType type, BigDecimal price, BigDecimal quantity)
                when !properties.isListed(symbol) ->
                throw new IllegalArgumentException("Symbole inconnu: " + symbol
                    + " (cotes: " + String.join(", ", properties.symbols().keySet()) + ")");
            case PlaceOrderRequest(String symbol, OrderSide side, OrderType type, BigDecimal price, BigDecimal quantity)
                when type == OrderType.LIMIT && price == null ->
                throw new IllegalArgumentException("Un ordre LIMIT doit avoir un prix");
            case PlaceOrderRequest(String symbol, OrderSide side, OrderType type, BigDecimal price, BigDecimal quantity)
                when type == OrderType.MARKET && price != null ->
                throw new IllegalArgumentException("Un ordre MARKET ne doit pas avoir de prix");
            default -> { /* requete coherente */ }
        }
    }

    /**
     * Un LIMIT est cree tel quel. Un MARKET recoit un prix plafond (BUY) ou
     * plancher (SELL): le pire prix qu'il faudrait atteindre dans le carnet
     * actuel pour executer toute la quantite. Les fonds sont bloques sur ce
     * prix, et le moteur refuse d'executer au-dela: un acheteur ne peut donc
     * jamais depenser plus que ce qui a ete reserve.
     */
    private Order buildOrder(String userId, PlaceOrderRequest request) {
        if (request.type() == OrderType.LIMIT) {
            return new Order(userId, request.symbol(), request.side(), OrderType.LIMIT,
                request.price(), request.quantity());
        }
        BigDecimal cap = matchingEngine.estimatePriceCap(request.symbol(), request.side(), request.quantity())
            .orElseThrow(() -> new IllegalStateException(
                "Aucune contrepartie sur " + request.symbol() + ": impossible de passer un ordre au marche"));
        return Order.marketWithCap(userId, request.symbol(), request.side(), request.quantity(), cap);
    }

    /**
     * Bloque les fonds (BUY) ou les actions (SELL) AVANT le matching: un
     * utilisateur ne peut jamais placer un ordre qu'il ne peut pas honorer.
     */
    private void reserve(String userId, OrderSide side, String symbol,
                         BigDecimal reservationPrice, BigDecimal quantity) {
        if (side == OrderSide.BUY) {
            WalletEntity wallet = lockWallet(userId);
            BigDecimal required = reservationPrice.multiply(quantity);
            if (required.compareTo(wallet.availableBalance()) > 0) {
                throw new InsufficientFundsException(
                    "Solde disponible insuffisant: requis=" + required + " $, disponible=" + wallet.availableBalance() + " $");
            }
            wallet.lock(required);
        } else {
            PositionEntity position = lockPosition(userId, symbol);
            if (quantity.compareTo(position.availableQuantity()) > 0) {
                throw new InsufficientFundsException(
                    "Actions " + symbol + " disponibles insuffisantes: requis=" + quantity
                    + ", disponible=" + position.availableQuantity());
            }
            position.lock(quantity);
        }
    }

    /**
     * Regle tous les trades d'une soumission: le cash passe de l'acheteur au
     * vendeur, les actions du vendeur a l'acheteur. Le montant debite est
     * TOUJOURS le prix d'execution reel; la reservation est liberee au prix
     * qui avait servi a la bloquer. La difference ("price improvement")
     * revient donc bien a l'acheteur.
     */
    private void settle(Order incoming, MatchResult result) {
        List<Trade> trades = result.trades();

        // Verrous dans un ordre global fixe: wallets, puis positions, puis ordres.
        SortedSet<String> userIds = new TreeSet<>();
        SortedSet<String> orderIds = new TreeSet<>();
        userIds.add(incoming.getUserId());
        orderIds.add(incoming.getId());
        for (Trade trade : trades) {
            userIds.add(trade.buyerId());
            userIds.add(trade.sellerId());
            orderIds.add(trade.buyOrderId());
            orderIds.add(trade.sellOrderId());
        }
        Map<String, WalletEntity> wallets = new HashMap<>();
        for (String userId : userIds) {
            wallets.put(userId, lockWallet(userId));
        }
        Map<String, PositionEntity> positions = new HashMap<>();
        for (String userId : userIds) {
            positions.put(userId, lockPosition(userId, incoming.getSymbol()));
        }
        Map<String, OrderEntity> orders = new HashMap<>();
        for (String orderId : orderIds) {
            orders.put(orderId, orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId)));
        }

        for (Trade trade : trades) {
            tradeRepository.save(new TradeEntity(trade.id(), trade.symbol(), trade.buyOrderId(),
                trade.sellOrderId(), trade.price(), trade.quantity(), trade.executedAt()));

            OrderEntity buyOrder = orders.get(trade.buyOrderId());
            BigDecimal cost = trade.notional();

            WalletEntity buyerWallet = wallets.get(trade.buyerId());
            buyerWallet.unlock(buyOrder.getReservationPrice().multiply(trade.quantity()));
            buyerWallet.debit(cost);
            positions.get(trade.buyerId()).credit(trade.quantity());

            positions.get(trade.sellerId()).settleDebit(trade.quantity());
            wallets.get(trade.sellerId()).credit(cost);
        }

        // Reliquat annule par le moteur (MARKET non entierement execute, ou
        // auto-trade evite): on libere la reservation correspondante.
        OrderEntity incomingEntity = orders.get(incoming.getId());
        incomingEntity.setSequence(incoming.getSequence());
        if (result.status() == OrderStatus.CANCELLED && result.remainingQuantity().signum() > 0) {
            releaseReservation(incomingEntity, result.remainingQuantity(),
                wallets.get(incoming.getUserId()), positions.get(incoming.getUserId()));
            incomingEntity.setStatus(OrderStatus.CANCELLED);
        }

        for (OrderEntity order : orders.values()) {
            refreshFillState(order);
        }
    }

    /**
     * Recalcule la quantite restante d'un ordre a partir de la table trades
     * (quantite initiale - somme de ses executions). Ce calcul ne depend pas
     * de l'ordre dans lequel les reglements concurrents sont commites.
     * Un ordre annule reste CANCELLED.
     */
    private void refreshFillState(OrderEntity order) {
        BigDecimal filled = tradeRepository.findByBuyOrderIdOrSellOrderId(order.getId(), order.getId()).stream()
            .map(TradeEntity::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = order.getQuantity().subtract(filled).max(BigDecimal.ZERO);
        order.setRemainingQuantity(remaining);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (remaining.signum() == 0) {
            order.setStatus(OrderStatus.FILLED);
        } else if (filled.signum() > 0) {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }

    private void releaseReservation(OrderEntity order, BigDecimal quantity,
                                    WalletEntity wallet, PositionEntity position) {
        if (order.getSide() == OrderSide.BUY) {
            wallet.unlock(order.getReservationPrice().multiply(quantity));
        } else {
            position.unlock(quantity);
        }
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    public void cancelOrder(String userId, String orderId) {
        // Lecture simple hors transaction: userId, symbole et sens ne changent jamais.
        OrderEntity snapshot = orderRepository.findById(orderId)
            .filter(o -> o.getUserId().equals(userId)) // ne pas reveler l'existence de l'ordre d'autrui
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        String symbol = snapshot.getSymbol();

        transactionTemplate.executeWithoutResult(status -> {
            // Meme ordre de verrouillage que le reglement: compte, puis ordre.
            WalletEntity wallet = snapshot.getSide() == OrderSide.BUY ? lockWallet(userId) : null;
            PositionEntity position = snapshot.getSide() == OrderSide.SELL ? lockPosition(userId, symbol) : null;
            OrderEntity order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

            // La quantite restante de reference est celle du moteur, lue sous son verrou.
            Order cancelled = matchingEngine.cancelOrder(symbol, orderId);

            releaseReservation(order, cancelled.getRemainingQuantity(), wallet, position);
            order.setRemainingQuantity(cancelled.getRemainingQuantity());
            order.setStatus(OrderStatus.CANCELLED);
        });

        broadcast(symbol, List.of());
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OrderEntity> getMyOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public OrderBookResponse getOrderBookSnapshot(String symbol) {
        requireListed(symbol);
        return matchingEngine.readBook(symbol, book -> new OrderBookResponse(
            symbol,
            summarizeLevels(book.viewBids()),
            summarizeLevels(book.viewAsks())
        ));
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
        requireListed(symbol);
        return tradeRepository.findTop50BySymbolOrderByExecutedAtDesc(symbol);
    }

    @Transactional(readOnly = true)
    public List<MarketSummaryResponse> getMarketSummaries() {
        List<MarketSummaryResponse> summaries = new ArrayList<>();
        properties.symbols().forEach((symbol, referencePrice) -> {
            BigDecimal lastPrice = tradeRepository.findTopBySymbolOrderByExecutedAtDesc(symbol)
                .map(TradeEntity::getPrice)
                .orElse(null);
            summaries.add(matchingEngine.readBook(symbol, (OrderBook book) -> new MarketSummaryResponse(
                symbol,
                referencePrice,
                lastPrice,
                book.bestBid().orElse(null),
                book.bestAsk().orElse(null)
            )));
        });
        return summaries;
    }

    @Transactional
    public WalletResponse getWallet(String userId) {
        ensureAccount(userId);
        WalletEntity wallet = walletRepository.findByUserId(userId).orElseThrow();
        return WalletResponse.of(wallet, positionRepository.findByUserIdOrderBySymbolAsc(userId));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Ouvre le compte d'un nouvel utilisateur: un wallet avec le solde de
     * demo, et une position de depart sur chaque symbole cote (sinon personne
     * ne pourrait jamais vendre, et aucun trade ne serait possible).
     */
    private void ensureAccount(String userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            return;
        }
        walletRepository.save(new WalletEntity(userId, properties.startingBalance()));
        for (String symbol : properties.symbols().keySet()) {
            PositionEntity position = new PositionEntity(userId, symbol);
            position.credit(properties.startingShares());
            positionRepository.save(position);
        }
        log.info("Nouveau compte ouvert pour {}", userId);
    }

    private WalletEntity lockWallet(String userId) {
        return walletRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> walletRepository.save(new WalletEntity(userId, properties.startingBalance())));
    }

    private PositionEntity lockPosition(String userId, String symbol) {
        return positionRepository.findByUserIdAndSymbolForUpdate(userId, symbol)
            .orElseGet(() -> positionRepository.save(new PositionEntity(userId, symbol)));
    }

    private void requireListed(String symbol) {
        if (!properties.isListed(symbol)) {
            throw new IllegalArgumentException("Symbole inconnu: " + symbol);
        }
    }

    private OrderEntity toNewEntity(Order order, BigDecimal reservationPrice) {
        return new OrderEntity(order.getId(), order.getUserId(), order.getSymbol(), order.getSide(),
            order.getType(), order.getPrice(), reservationPrice, order.getQuantity(),
            order.getRemainingQuantity(), order.getStatus(), order.getCreatedAt(), order.getSequence());
    }

    private void broadcast(String symbol, List<Trade> trades) {
        for (Trade trade : trades) {
            broadcaster.publish(new MarketEvent.TradeExecuted(TradeResponse.from(trade)));
        }
        broadcaster.publish(new MarketEvent.OrderBookUpdated(getOrderBookSnapshot(symbol)));
    }
}
