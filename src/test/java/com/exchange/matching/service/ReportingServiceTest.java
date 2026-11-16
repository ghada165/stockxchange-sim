package com.exchange.matching.service;

import com.exchange.matching.dto.CandleResponse;
import com.exchange.matching.dto.SymbolStatsResponse;
import com.exchange.matching.dto.TradeHistoryResponse;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.persistence.entity.TradeEntity;
import com.exchange.matching.persistence.repository.TradeRepository;
import com.exchange.matching.reporting.TradeStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock private TradeRepository tradeRepository;
    @Mock private SymbolService symbolService;

    @InjectMocks
    private ReportingService service;

    private static TradeEntity trade(String id, String buyer, String seller, String price, String qty, Instant at) {
        return new TradeEntity(id, "AAPL", "b-" + id, "s-" + id, buyer, seller, new BigDecimal(price), new BigDecimal(qty), at);
    }

    // ---------- Chandeliers ----------

    @Test
    void candles_shouldAggregateTradesByInterval() {
        Instant t0 = Instant.parse("2026-11-16T10:00:10Z");
        when(tradeRepository.findBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(eq("AAPL"), any())).thenReturn(List.of(
            trade("1", "a", "b", "230", "1", t0),
            trade("2", "a", "b", "232", "2", t0.plusSeconds(20)),
            trade("3", "a", "b", "231", "1", t0.plusSeconds(70))));

        List<CandleResponse> candles = service.candles("AAPL", "1m");

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).open()).isEqualByComparingTo("230");
        assertThat(candles.get(0).high()).isEqualByComparingTo("232");
        assertThat(candles.get(0).volume()).isEqualByComparingTo("3");
        assertThat(candles.get(1).close()).isEqualByComparingTo("231");
        verify(symbolService).require("AAPL");
    }

    @Test
    void candles_withUnknownInterval_shouldThrowWithoutQueryingTrades() {
        assertThatThrownBy(() -> service.candles("AAPL", "2d"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Intervalle inconnu: 2d");

        verify(tradeRepository, never()).findBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(anyString(), any());
    }

    @Test
    void candles_withUnknownSymbol_shouldThrow() {
        when(symbolService.require("XXX")).thenThrow(new IllegalArgumentException("Symbole inconnu: XXX"));

        assertThatThrownBy(() -> service.candles("XXX", "1m")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Statistiques ----------

    @Test
    void stats_shouldComputeAveragePriceAndChange() {
        when(tradeRepository.statsForSymbol(eq("AAPL"), any())).thenReturn(
            new TradeStats(3L, new BigDecimal("4"), new BigDecimal("926"), new BigDecimal("235"), new BigDecimal("230")));
        when(tradeRepository.findFirstBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(eq("AAPL"), any()))
            .thenReturn(Optional.of(trade("1", "a", "b", "230", "1", Instant.now())));
        when(tradeRepository.findTopBySymbolOrderByExecutedAtDesc("AAPL"))
            .thenReturn(Optional.of(trade("3", "a", "b", "234.60", "1", Instant.now())));

        SymbolStatsResponse stats = service.stats("AAPL");

        assertThat(stats.tradeCount()).isEqualTo(3);
        assertThat(stats.averagePrice()).isEqualByComparingTo("231.5");    // 926 / 4
        assertThat(stats.changePercent()).isEqualByComparingTo("2.00");    // (234.60 - 230) / 230
        assertThat(stats.high()).isEqualByComparingTo("235");
    }

    @Test
    void stats_withoutAnyTrade_shouldReturnNullPrices() {
        when(tradeRepository.statsForSymbol(eq("AAPL"), any())).thenReturn(new TradeStats(null, null, null, null, null));
        when(tradeRepository.findFirstBySymbolAndExecutedAtGreaterThanEqualOrderByExecutedAtAsc(eq("AAPL"), any()))
            .thenReturn(Optional.empty());
        when(tradeRepository.findTopBySymbolOrderByExecutedAtDesc("AAPL")).thenReturn(Optional.empty());

        SymbolStatsResponse stats = service.stats("AAPL");

        assertThat(stats.tradeCount()).isZero();
        assertThat(stats.volume()).isEqualByComparingTo("0");
        assertThat(stats.averagePrice()).isNull();
        assertThat(stats.changePercent()).isNull();
    }

    @Test
    void globalStats_shouldDelegateToRepository() {
        TradeStats expected = new TradeStats(1L, BigDecimal.ONE, BigDecimal.TEN, null, null);
        when(tradeRepository.statsForAll(any())).thenReturn(expected);

        assertThat(service.globalStats()).isEqualTo(expected);
    }

    // ---------- Releve personnel ----------

    @Test
    void history_shouldTagSideFromUserPointOfViewAndSummarize() {
        when(tradeRepository.findTop500ByBuyerIdOrSellerIdOrderByExecutedAtDesc("alice", "alice")).thenReturn(List.of(
            trade("1", "alice", "bob", "230", "2", Instant.now()),     // achat de 460
            trade("2", "carol", "alice", "235", "1", Instant.now()))); // vente de 235

        TradeHistoryResponse history = service.history("alice");

        assertThat(history.trades()).extracting(t -> t.side()).containsExactly(OrderSide.BUY, OrderSide.SELL);
        assertThat(history.summary().buyCount()).isEqualTo(1);
        assertThat(history.summary().sellCount()).isEqualTo(1);
        assertThat(history.summary().totalBought()).isEqualByComparingTo("460");
        assertThat(history.summary().totalSold()).isEqualByComparingTo("235");
        assertThat(history.summary().netCashFlow()).isEqualByComparingTo("-225");
    }

    @Test
    void history_withoutTrades_shouldReturnEmptyStatement() {
        when(tradeRepository.findTop500ByBuyerIdOrSellerIdOrderByExecutedAtDesc("dave", "dave")).thenReturn(List.of());

        TradeHistoryResponse history = service.history("dave");

        assertThat(history.trades()).isEmpty();
        assertThat(history.summary().tradeCount()).isZero();
        assertThat(history.summary().netCashFlow()).isEqualByComparingTo("0");
    }
}
