package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.OrderBookResponse;
import com.exchange.matching.dto.PlaceOrderRequest;
import com.exchange.matching.dto.PlaceOrderResponse;
import com.exchange.matching.dto.WalletResponse;
import com.exchange.matching.engine.MatchingEngine;
import com.exchange.matching.event.MarketBroadcaster;
import com.exchange.matching.event.MarketEvent;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.exception.InsufficientFundsException;
import com.exchange.matching.exception.MarketClosedException;
import com.exchange.matching.exception.OrderNotFoundException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.OrderEntity;
import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.entity.TradeEntity;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.entity.WalletEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import com.exchange.matching.persistence.repository.PositionRepository;
import com.exchange.matching.persistence.repository.TradeRepository;
import com.exchange.matching.persistence.repository.UserRepository;
import com.exchange.matching.persistence.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de TradingService: reservation, matching, reglement, annulation.
 *
 * Le MatchingEngine est le vrai (pur, en memoire). Les repositories sont des
 * mocks Mockito branches sur de simples Map: ce qui est "sauvegarde" peut etre
 * relu, ce qui permet de verifier les soldes apres reglement sans base de donnees.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradingServiceTest {

    private static final String AAPL = "AAPL";

    @Mock private OrderRepository orderRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private MarketBroadcaster broadcaster;
    @Mock private SymbolService symbolService;
    @Mock private MarketHoursService marketHours;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogService activityLog;

    private MatchingEngine engine;
    private TradingService service;

    // "Base de donnees" en memoire derriere les mocks
    private final Map<String, WalletEntity> wallets = new HashMap<>();
    private final Map<String, PositionEntity> positions = new HashMap<>();
    private final Map<String, OrderEntity> orders = new HashMap<>();
    private final List<TradeEntity> trades = new ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine();
        ExchangeProperties properties = new ExchangeProperties(new BigDecimal("100000"), new BigDecimal("100"),
            List.of(), false, false, null, null);
        service = new TradingService(engine, orderRepository, tradeRepository, walletRepository, positionRepository,
            broadcaster, properties, new TransactionTemplate(mock(PlatformTransactionManager.class)),
            symbolService, marketHours, userRepository, activityLog);

        SymbolEntity apple = new SymbolEntity(AAPL, "Apple Inc.", new BigDecimal("230.00"));
        when(symbolService.require(AAPL)).thenReturn(apple);
        when(symbolService.requireTradable(AAPL)).thenReturn(apple);
        when(symbolService.codes()).thenReturn(List.of(AAPL, "TSLA"));
        when(symbolService.list()).thenReturn(List.of(apple));

        when(walletRepository.findByUserId(anyString())).thenAnswer(i -> Optional.ofNullable(wallets.get(i.<String>getArgument(0))));
        when(walletRepository.findByUserIdForUpdate(anyString())).thenAnswer(i -> Optional.ofNullable(wallets.get(i.<String>getArgument(0))));
        when(walletRepository.save(any(WalletEntity.class))).thenAnswer(i -> {
            WalletEntity w = i.getArgument(0);
            wallets.put(w.getUserId(), w);
            return w;
        });
        when(positionRepository.findByUserIdAndSymbolForUpdate(anyString(), anyString()))
            .thenAnswer(i -> Optional.ofNullable(positions.get(i.getArgument(0) + "|" + i.getArgument(1))));
        when(positionRepository.save(any(PositionEntity.class))).thenAnswer(i -> {
            PositionEntity p = i.getArgument(0);
            positions.put(p.getUserId() + "|" + p.getSymbol(), p);
            return p;
        });
        when(positionRepository.findByUserIdOrderBySymbolAsc(anyString())).thenAnswer(i -> positions.values().stream()
            .filter(p -> p.getUserId().equals(i.getArgument(0))).sorted((a, b) -> a.getSymbol().compareTo(b.getSymbol())).toList());
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(i -> {
            OrderEntity o = i.getArgument(0);
            orders.put(o.getId(), o);
            return o;
        });
        when(orderRepository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(orders.get(i.<String>getArgument(0))));
        when(orderRepository.findByIdForUpdate(anyString())).thenAnswer(i -> Optional.ofNullable(orders.get(i.<String>getArgument(0))));
        when(tradeRepository.save(any(TradeEntity.class))).thenAnswer(i -> {
            trades.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(tradeRepository.findByBuyOrderIdOrSellOrderId(anyString(), anyString())).thenAnswer(i -> trades.stream()
            .filter(t -> t.getBuyOrderId().equals(i.getArgument(0)) || t.getSellOrderId().equals(i.getArgument(1))).toList());
    }

    /** Ouvre un compte avec un solde et un nombre d'actions AAPL donnes. */
    private void account(String user, String cash, String aaplShares) {
        wallets.put(user, new WalletEntity(user, new BigDecimal(cash)));
        PositionEntity position = new PositionEntity(user, AAPL);
        position.credit(new BigDecimal(aaplShares));
        positions.put(user + "|" + AAPL, position);
    }

    private static PlaceOrderRequest limit(OrderSide side, String price, String quantity) {
        return new PlaceOrderRequest(AAPL, side, OrderType.LIMIT, new BigDecimal(price), new BigDecimal(quantity));
    }

    private WalletEntity wallet(String user) { return wallets.get(user); }
    private Optional<BigDecimal> bestBid() { return engine.readBook(AAPL, book -> book.bestBid()); }
    private boolean bookIsEmpty() { return engine.readBook(AAPL, book -> book.isEmpty()); }
    private PositionEntity aapl(String user) { return positions.get(user + "|" + AAPL); }

    // ------------------------------------------------------------------
    // Placement
    // ------------------------------------------------------------------

    @Test
    void placeOrder_limitBuyWithoutCounterparty_shouldReserveFundsAndRestInBook() {
        account("alice", "100000", "100");

        PlaceOrderResponse response = service.placeOrder("alice", limit(OrderSide.BUY, "200.00", "10"));

        assertThat(response.order().status()).isEqualTo(OrderStatus.OPEN);
        assertThat(response.trades()).isEmpty();
        assertThat(wallet("alice").getLockedBalance()).isEqualByComparingTo("2000.00"); // 10 x 200
        assertThat(wallet("alice").getBalance()).isEqualByComparingTo("100000");
        assertThat(orders.get(response.order().id()).getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(bestBid()).contains(new BigDecimal("200.00"));
        verify(broadcaster).publish(isA(MarketEvent.OrderBookUpdated.class));
        verify(activityLog).record(eq("alice"), eq(ActivityType.ORDER_PLACED), contains("OPEN"));
    }

    @Test
    void placeOrder_crossingOrders_shouldSettleCashAndSharesAtRestingPrice() {
        account("alice", "100000", "100");
        account("bob", "100000", "100");
        service.placeOrder("bob", limit(OrderSide.SELL, "230.00", "5"));

        PlaceOrderResponse response = service.placeOrder("alice", limit(OrderSide.BUY, "231.00", "5"));

        assertThat(response.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(response.trades()).hasSize(1);
        assertThat(response.trades().get(0).price()).isEqualByComparingTo("230.00"); // prix de l'ordre passif
        assertThat(trades).hasSize(1);

        // Acheteur: paie 5 x 230 = 1150, la reservation a 231 est entierement liberee
        assertThat(wallet("alice").getBalance()).isEqualByComparingTo("98850");
        assertThat(wallet("alice").getLockedBalance()).isEqualByComparingTo("0");
        assertThat(aapl("alice").getQuantity()).isEqualByComparingTo("105");
        // Vendeur: recoit 1150, ses actions bloquees sont livrees
        assertThat(wallet("bob").getBalance()).isEqualByComparingTo("101150");
        assertThat(aapl("bob").getQuantity()).isEqualByComparingTo("95");
        assertThat(aapl("bob").getLockedQuantity()).isEqualByComparingTo("0");

        assertThat(orders.values()).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OrderStatus.FILLED));
        verify(broadcaster).publish(isA(MarketEvent.TradeExecuted.class));
    }

    @Test
    void placeOrder_marketBuy_shouldExecuteAgainstBestAsk() {
        account("alice", "100000", "100");
        account("bob", "100000", "100");
        service.placeOrder("bob", limit(OrderSide.SELL, "230.00", "3"));

        PlaceOrderResponse response = service.placeOrder("alice",
            new PlaceOrderRequest(AAPL, OrderSide.BUY, OrderType.MARKET, null, new BigDecimal("3")));

        assertThat(response.order().status()).isEqualTo(OrderStatus.FILLED);
        assertThat(wallet("alice").getBalance()).isEqualByComparingTo("99310"); // 100000 - 3 x 230
        assertThat(wallet("alice").getLockedBalance()).isEqualByComparingTo("0");
    }

    @Test
    void placeOrder_marketWithEmptyBook_shouldBeRefused() {
        account("alice", "100000", "100");

        assertThatThrownBy(() -> service.placeOrder("alice",
                new PlaceOrderRequest(AAPL, OrderSide.BUY, OrderType.MARKET, null, BigDecimal.ONE)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Aucune contrepartie");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_withInsufficientCash_shouldThrowAndSaveNothing() {
        account("alice", "100", "100");

        assertThatThrownBy(() -> service.placeOrder("alice", limit(OrderSide.BUY, "200.00", "1")))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("Solde disponible insuffisant");

        verify(orderRepository, never()).save(any());
        assertThat(wallet("alice").getLockedBalance()).isEqualByComparingTo("0");
        assertThat(bookIsEmpty()).isTrue();
    }

    @Test
    void placeOrder_sellingMoreSharesThanOwned_shouldThrow() {
        account("alice", "100000", "10");

        assertThatThrownBy(() -> service.placeOrder("alice", limit(OrderSide.SELL, "230.00", "11")))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("Actions AAPL disponibles insuffisantes");
    }

    @Test
    void placeOrder_limitWithoutPrice_shouldThrowIllegalArgument() {
        account("alice", "100000", "100");

        assertThatThrownBy(() -> service.placeOrder("alice",
                new PlaceOrderRequest(AAPL, OrderSide.BUY, OrderType.LIMIT, null, BigDecimal.ONE)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Un ordre LIMIT doit avoir un prix");
    }

    @Test
    void placeOrder_bySuspendedUser_shouldBeRejectedAndLogged() {
        UserEntity eve = new UserEntity("eve", "hash", null, null, Role.TRADER);
        eve.setStatus(AccountStatus.SUSPENDED);
        when(userRepository.findByUsername("eve")).thenReturn(Optional.of(eve));

        assertThatThrownBy(() -> service.placeOrder("eve", limit(OrderSide.BUY, "200.00", "1")))
            .isInstanceOf(ForbiddenOperationException.class);

        verify(activityLog).record(eq("eve"), eq(ActivityType.ORDER_REJECTED), contains("suspendu"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_whenMarketClosed_shouldBeRejectedAndLogged() {
        doThrow(new MarketClosedException("Marche ferme")).when(marketHours).requireOpen();

        assertThatThrownBy(() -> service.placeOrder("alice", limit(OrderSide.BUY, "200.00", "1")))
            .isInstanceOf(MarketClosedException.class);

        verify(activityLog).record(eq("alice"), eq(ActivityType.ORDER_REJECTED), contains("Marche ferme"));
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    @Test
    void cancelOrder_shouldReleaseReservationAndRemoveFromBook() {
        account("alice", "100000", "100");
        String orderId = service.placeOrder("alice", limit(OrderSide.BUY, "200.00", "10")).order().id();

        service.cancelOrder("alice", orderId);

        assertThat(orders.get(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(wallet("alice").getLockedBalance()).isEqualByComparingTo("0");
        assertThat(bookIsEmpty()).isTrue();
        verify(activityLog).record(eq("alice"), eq(ActivityType.ORDER_CANCELLED), contains(AAPL));
    }

    @Test
    void cancelOrder_whenUnknown_shouldThrowOrderNotFound() {
        assertThatThrownBy(() -> service.cancelOrder("alice", "nope"))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelOrder_ofAnotherUser_shouldThrowAndKeepOrder() {
        account("bob", "100000", "100");
        String bobOrder = service.placeOrder("bob", limit(OrderSide.SELL, "250.00", "2")).order().id();

        assertThatThrownBy(() -> service.cancelOrder("alice", bobOrder))
            .isInstanceOf(OrderNotFoundException.class);

        assertThat(orders.get(bobOrder).getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(aapl("bob").getLockedQuantity()).isEqualByComparingTo("2");
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Test
    void getWallet_forNewUser_shouldOpenAccountWithStartingBalanceAndShares() {
        WalletResponse wallet = service.getWallet("dave");

        assertThat(wallet.balance()).isEqualByComparingTo("100000");
        assertThat(wallet.availableBalance()).isEqualByComparingTo("100000");
        assertThat(wallet.positions()).extracting(p -> p.symbol()).containsExactly(AAPL, "TSLA");
        assertThat(wallet.positions()).allSatisfy(p -> assertThat(p.quantity()).isEqualByComparingTo("100"));
    }

    @Test
    void getMyOrders_shouldDelegateToRepository() {
        OrderEntity order = new OrderEntity("o-1", "alice", AAPL, OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN,
            BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, OrderStatus.OPEN, Instant.now(), 1);
        when(orderRepository.findByUserIdOrderByCreatedAtDesc("alice")).thenReturn(List.of(order));

        assertThat(service.getMyOrders("alice")).containsExactly(order);
    }

    @Test
    void getRecentTrades_withUnknownSymbol_shouldThrow() {
        when(symbolService.require("XXX")).thenThrow(new IllegalArgumentException("Symbole inconnu: XXX"));

        assertThatThrownBy(() -> service.getRecentTrades("XXX")).isInstanceOf(IllegalArgumentException.class);
        verify(tradeRepository, never()).findTop50BySymbolOrderByExecutedAtDesc(anyString());
    }

    @Test
    void getMarketSummaries_shouldCombineLastPriceAndBestPrices() {
        account("alice", "100000", "100");
        account("bob", "100000", "100");
        service.placeOrder("alice", limit(OrderSide.BUY, "229.00", "1"));
        service.placeOrder("bob", limit(OrderSide.SELL, "231.00", "1"));
        when(tradeRepository.findTopBySymbolOrderByExecutedAtDesc(AAPL)).thenReturn(Optional.of(new TradeEntity(
            "t", AAPL, "b", "s", "x", "y", new BigDecimal("230.10"), BigDecimal.ONE, Instant.now())));

        List<MarketSummaryResponse> summaries = service.getMarketSummaries();

        assertThat(summaries).hasSize(1);
        MarketSummaryResponse aapl = summaries.get(0);
        assertThat(aapl.lastPrice()).isEqualByComparingTo("230.10");
        assertThat(aapl.bestBid()).isEqualByComparingTo("229.00");
        assertThat(aapl.bestAsk()).isEqualByComparingTo("231.00");
    }

    @Test
    void getOrderBookSnapshot_shouldAggregateLevels() {
        account("alice", "100000", "100");
        account("bob", "100000", "100");
        service.placeOrder("alice", limit(OrderSide.BUY, "229.00", "2"));
        service.placeOrder("bob", limit(OrderSide.BUY, "229.00", "3"));

        OrderBookResponse book = service.getOrderBookSnapshot(AAPL);

        assertThat(book.bids()).hasSize(1);
        assertThat(book.bids().get(0).totalQuantity()).isEqualByComparingTo("5");
        assertThat(book.bids().get(0).orderCount()).isEqualTo(2);
        assertThat(book.asks()).isEmpty();
    }
}
