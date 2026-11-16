package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.config.MarketSeeder;
import com.exchange.matching.dto.CreateSymbolRequest;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.UpdateSymbolRequest;
import com.exchange.matching.exception.ResourceNotFoundException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.persistence.entity.OrderEntity;
import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.entity.WalletEntity;
import com.exchange.matching.persistence.repository.OrderRepository;
import com.exchange.matching.persistence.repository.PositionRepository;
import com.exchange.matching.persistence.repository.SymbolRepository;
import com.exchange.matching.persistence.repository.TradeRepository;
import com.exchange.matching.persistence.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du service CRUD des symboles (TP 5, tache 2).
 * Les repositories et les autres services sont des mocks Mockito; le
 * TransactionTemplate est reel mais pilote un gestionnaire de transactions
 * factice: les callbacks s'executent directement, sans base de donnees.
 */
@ExtendWith(MockitoExtension.class)
class SymbolAdminServiceTest {

    private static final List<OrderStatus> ACTIVE = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);

    @Mock private SymbolRepository symbolRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private PositionRepository positionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TradeRepository tradeRepository;
    @Mock private TradingService tradingService;
    @Mock private MarketSeeder marketSeeder;
    @Mock private ActivityLogService activityLog;

    private SymbolAdminService service;
    private SymbolEntity apple;

    @BeforeEach
    void setUp() {
        ExchangeProperties properties = new ExchangeProperties(new BigDecimal("100000"), new BigDecimal("100"),
            List.of(), false, false, null, null);
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        service = new SymbolAdminService(symbolRepository, walletRepository, positionRepository, orderRepository,
            tradeRepository, tradingService, marketSeeder, activityLog, properties, tx);
        apple = new SymbolEntity("AAPL", "Apple Inc.", new BigDecimal("230.00"));
    }

    private static MarketSummaryResponse summary(SymbolEntity s) {
        return new MarketSummaryResponse(s.getSymbol(), s.getName(), s.getStatus(), s.getReferencePrice(), null, null, null);
    }

    // ---------- CREATE ----------

    @Test
    void create_shouldSaveSymbolGiveSharesToEveryWalletAndSeedTheBook() {
        CreateSymbolRequest request = new CreateSymbolRequest("NVDA", "  Nvidia Corp. ", new BigDecimal("140.00"));
        when(symbolRepository.existsById("NVDA")).thenReturn(false);
        when(symbolRepository.save(any(SymbolEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.findAll()).thenReturn(List.of(
            new WalletEntity("alice", new BigDecimal("100000")), new WalletEntity("bob", new BigDecimal("100000"))));
        when(tradingService.getMarketSummaries()).thenAnswer(inv ->
            List.of(summary(new SymbolEntity("NVDA", "Nvidia Corp.", new BigDecimal("140.00")))));

        MarketSummaryResponse result = service.create("admin", request);

        ArgumentCaptor<SymbolEntity> saved = ArgumentCaptor.forClass(SymbolEntity.class);
        verify(symbolRepository).save(saved.capture());
        assertThat(saved.getValue().getSymbol()).isEqualTo("NVDA");
        assertThat(saved.getValue().getName()).isEqualTo("Nvidia Corp."); // nom nettoye
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.ACTIVE);

        ArgumentCaptor<PositionEntity> positions = ArgumentCaptor.forClass(PositionEntity.class);
        verify(positionRepository, times(2)).save(positions.capture());
        assertThat(positions.getAllValues()).extracting(PositionEntity::getUserId).containsExactly("alice", "bob");
        assertThat(positions.getAllValues()).allSatisfy(p -> assertThat(p.getQuantity()).isEqualByComparingTo("100"));

        verify(tradingService).openAccount(TradingService.MARKET_MAKER);
        verify(activityLog).record(eq("admin"), eq(ActivityType.SYMBOL_CREATED), contains("NVDA"));
        verify(marketSeeder).seed("NVDA", new BigDecimal("140.00"));
        assertThat(result.symbol()).isEqualTo("NVDA");
    }

    @Test
    void create_whenSymbolExists_shouldThrowAndNotSave() {
        when(symbolRepository.existsById("AAPL")).thenReturn(true);

        assertThatThrownBy(() -> service.create("admin", new CreateSymbolRequest("AAPL", "Apple", new BigDecimal("1"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("existe deja");

        verify(symbolRepository, never()).save(any());
        verify(marketSeeder, never()).seed(any(), any());
    }

    // ---------- READ ALL ----------

    @Test
    void findAll_shouldReturnAllSummaries() {
        SymbolEntity tesla = new SymbolEntity("TSLA", "Tesla Inc.", new BigDecimal("250.00"));
        when(tradingService.getMarketSummaries()).thenReturn(List.of(summary(apple), summary(tesla)));

        List<MarketSummaryResponse> result = service.findAll();

        assertThat(result).extracting(MarketSummaryResponse::symbol).containsExactly("AAPL", "TSLA");
    }

    @Test
    void findAll_whenNoSymbol_shouldReturnEmptyList() {
        when(tradingService.getMarketSummaries()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    // ---------- READ BY ID ----------

    @Test
    void findById_whenExists_shouldReturnSummary() {
        when(symbolRepository.findById("AAPL")).thenReturn(Optional.of(apple));
        when(tradingService.getMarketSummaries()).thenReturn(List.of(summary(apple)));

        MarketSummaryResponse result = service.findById("AAPL");

        assertThat(result.name()).isEqualTo("Apple Inc.");
        assertThat(result.referencePrice()).isEqualByComparingTo("230.00");
    }

    @Test
    void findById_whenNotFound_shouldThrowResourceNotFound() {
        when(symbolRepository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("XXX"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Symbole inconnu: XXX");

        verify(tradingService, never()).getMarketSummaries();
    }

    // ---------- UPDATE ----------

    @Test
    void update_shouldFindModifyAndSaveSymbol() {
        when(symbolRepository.findById("AAPL")).thenReturn(Optional.of(apple));
        when(tradingService.getMarketSummaries()).thenAnswer(inv -> List.of(summary(apple)));

        MarketSummaryResponse result = service.update("admin", "AAPL",
            new UpdateSymbolRequest(" Apple Computer ", new BigDecimal("235.50")));

        // 1. recuperation, 2. modification, 3. sauvegarde
        InOrder order = inOrder(symbolRepository);
        order.verify(symbolRepository).findById("AAPL");
        ArgumentCaptor<SymbolEntity> saved = ArgumentCaptor.forClass(SymbolEntity.class);
        order.verify(symbolRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Apple Computer");
        assertThat(saved.getValue().getReferencePrice()).isEqualByComparingTo("235.50");

        verify(activityLog).record(eq("admin"), eq(ActivityType.SYMBOL_UPDATED), contains("AAPL"));
        assertThat(result.name()).isEqualTo("Apple Computer");
    }

    @Test
    void update_whenNotFound_shouldThrowAndNotSave() {
        when(symbolRepository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("admin", "XXX", new UpdateSymbolRequest("X", BigDecimal.ONE)))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(symbolRepository, never()).save(any());
        verify(activityLog, never()).record(any(), any(), any());
    }

    @Test
    void setStatus_shouldSuspendSymbolAndLog() {
        when(symbolRepository.findById("AAPL")).thenReturn(Optional.of(apple));
        when(tradingService.getMarketSummaries()).thenAnswer(inv -> List.of(summary(apple)));

        MarketSummaryResponse result = service.setStatus("admin", "AAPL", AccountStatus.SUSPENDED);

        assertThat(apple.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(result.status()).isEqualTo(AccountStatus.SUSPENDED);
        verify(symbolRepository).save(apple);
        verify(activityLog).record("admin", ActivityType.SYMBOL_STATUS_CHANGED, "AAPL -> SUSPENDED");
    }

    @Test
    void setStatus_whenNotFound_shouldThrow() {
        when(symbolRepository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setStatus("admin", "XXX", AccountStatus.ACTIVE))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- DELETE ----------

    @Test
    void delete_whenNeverTraded_shouldCancelMarketMakerOrdersAndDeleteEverything() {
        OrderEntity makerOrder = new OrderEntity("mm-1", TradingService.MARKET_MAKER, "AAPL", OrderSide.SELL, OrderType.LIMIT,
            new BigDecimal("230.46"), new BigDecimal("230.46"), new BigDecimal("4"), new BigDecimal("4"),
            OrderStatus.OPEN, Instant.now(), 1);
        when(symbolRepository.findById("AAPL")).thenReturn(Optional.of(apple));
        when(tradeRepository.existsBySymbol("AAPL")).thenReturn(false);
        when(orderRepository.existsBySymbolAndUserIdNot("AAPL", TradingService.MARKET_MAKER)).thenReturn(false);
        when(orderRepository.findBySymbolAndUserIdAndStatusIn("AAPL", TradingService.MARKET_MAKER, ACTIVE))
            .thenReturn(List.of(makerOrder));

        service.delete("admin", "AAPL");

        verify(tradingService).cancelOrder(TradingService.MARKET_MAKER, "mm-1");
        InOrder order = inOrder(orderRepository, positionRepository, symbolRepository);
        order.verify(orderRepository).deleteAllBySymbol("AAPL");
        order.verify(positionRepository).deleteAllBySymbol("AAPL");
        order.verify(symbolRepository).deleteById("AAPL");
        verify(activityLog).record(eq("admin"), eq(ActivityType.SYMBOL_DELETED), contains("AAPL"));
    }

    @Test
    void delete_whenNotFound_shouldThrowAndDeleteNothing() {
        when(symbolRepository.findById("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("admin", "XXX"))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(symbolRepository, never()).deleteById(anyString());
        verify(orderRepository, never()).deleteAllBySymbol(anyString());
    }

    @Test
    void delete_whenAlreadyTraded_shouldThrowAndKeepSymbol() {
        when(symbolRepository.findById("AAPL")).thenReturn(Optional.of(apple));
        when(tradeRepository.existsBySymbol("AAPL")).thenReturn(true);

        assertThatThrownBy(() -> service.delete("admin", "AAPL"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Suspendez la cotation");

        verify(symbolRepository, never()).deleteById(anyString());
        verify(tradingService, never()).cancelOrder(anyString(), anyString());
        assertThat(apple.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void delete_whenOrderArrivesDuringDeletion_shouldRestoreStatus() {
        when(symbolRepository.findById("AAPL")).thenReturn(Optional.of(apple));
        when(tradeRepository.existsBySymbol("AAPL")).thenReturn(false);
        // premier controle: aucun ordre; second controle (apres suspension): un trader vient d'en passer un
        when(orderRepository.existsBySymbolAndUserIdNot("AAPL", TradingService.MARKET_MAKER)).thenReturn(false, true);

        assertThatThrownBy(() -> service.delete("admin", "AAPL")).isInstanceOf(IllegalStateException.class);

        assertThat(apple.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(symbolRepository, never()).deleteById(anyString());
    }
}
