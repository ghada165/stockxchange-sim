package com.exchange.matching.controller;

import com.exchange.matching.dto.CandleResponse;
import com.exchange.matching.dto.MarketStatusResponse;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.OrderBookLevelDto;
import com.exchange.matching.dto.OrderBookResponse;
import com.exchange.matching.dto.SymbolStatsResponse;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.persistence.entity.TradeEntity;
import com.exchange.matching.service.MarketHoursService;
import com.exchange.matching.service.ReportingService;
import com.exchange.matching.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints publics: aucun utilisateur connecte n'est necessaire. */
@WebMvcTest(MarketController.class)
@AutoConfigureMockMvc(addFilters = false)
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingService tradingService;
    @MockBean
    private ReportingService reportingService;
    @MockBean
    private MarketHoursService marketHoursService;

    @Test
    void status_shouldReturnMarketHours() throws Exception {
        when(marketHoursService.status()).thenReturn(new MarketStatusResponse(false, true, LocalTime.of(9, 0),
            LocalTime.of(17, 30), "Europe/Paris", List.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Instant.now()));

        mockMvc.perform(get("/api/market/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(false))
            .andExpect(jsonPath("$.openTime").value("09:00:00"))
            .andExpect(jsonPath("$.tradingDays", hasSize(2)));
    }

    @Test
    void symbols_shouldReturnAllListedSymbols() throws Exception {
        when(tradingService.getMarketSummaries()).thenReturn(List.of(
            new MarketSummaryResponse("AAPL", "Apple Inc.", AccountStatus.ACTIVE, new BigDecimal("230.00"),
                new BigDecimal("230.46"), new BigDecimal("229.54"), new BigDecimal("230.92"))));

        mockMvc.perform(get("/api/market/symbols"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$[0].lastPrice").value(230.46));
    }

    @Test
    void orderBook_shouldReturnBidsAndAsks() throws Exception {
        when(tradingService.getOrderBookSnapshot("AAPL")).thenReturn(new OrderBookResponse("AAPL",
            List.of(new OrderBookLevelDto(new BigDecimal("229.54"), new BigDecimal("4"), 1)),
            List.of(new OrderBookLevelDto(new BigDecimal("230.46"), new BigDecimal("4"), 1),
                new OrderBookLevelDto(new BigDecimal("230.92"), new BigDecimal("6"), 1))));

        mockMvc.perform(get("/api/market/AAPL/orderbook"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bids", hasSize(1)))
            .andExpect(jsonPath("$.asks", hasSize(2)))
            .andExpect(jsonPath("$.asks[0].price").value(230.46));
    }

    @Test
    void orderBook_withUnknownSymbol_shouldReturn400() throws Exception {
        when(tradingService.getOrderBookSnapshot("XXX")).thenThrow(new IllegalArgumentException("Symbole inconnu: XXX"));

        mockMvc.perform(get("/api/market/XXX/orderbook"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Symbole inconnu: XXX"));
    }

    @Test
    void recentTrades_shouldMapEntitiesToResponses() throws Exception {
        TradeEntity trade = new TradeEntity("t-1", "AAPL", "b-1", "s-1", "alice", "bob",
            new BigDecimal("230.46"), new BigDecimal("3"), Instant.parse("2026-11-16T10:00:00Z"));
        when(tradingService.getRecentTrades("AAPL")).thenReturn(List.of(trade));

        mockMvc.perform(get("/api/market/AAPL/trades"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value("t-1"))
            .andExpect(jsonPath("$[0].quantity").value(3))
            // le releve public ne revele pas qui a achete ou vendu
            .andExpect(jsonPath("$[0].buyerId").doesNotExist());
    }

    @Test
    void candles_shouldUseRequestedInterval() throws Exception {
        when(reportingService.candles("AAPL", "5m")).thenReturn(List.of(new CandleResponse(Instant.parse("2026-11-16T10:00:00Z"),
            new BigDecimal("230"), new BigDecimal("232"), new BigDecimal("229"), new BigDecimal("231"), new BigDecimal("12"))));

        mockMvc.perform(get("/api/market/AAPL/candles").param("interval", "5m"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].high").value(232));

        verify(reportingService).candles("AAPL", "5m");
    }

    @Test
    void candles_withoutInterval_shouldDefaultToOneMinute() throws Exception {
        when(reportingService.candles("AAPL", "1m")).thenReturn(List.of());

        mockMvc.perform(get("/api/market/AAPL/candles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        verify(reportingService).candles("AAPL", "1m");
    }

    @Test
    void candles_withUnknownInterval_shouldReturn400() throws Exception {
        when(reportingService.candles("AAPL", "2d")).thenThrow(new IllegalArgumentException("Intervalle inconnu: 2d"));

        mockMvc.perform(get("/api/market/AAPL/candles").param("interval", "2d"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void stats_shouldReturnStatistics() throws Exception {
        when(reportingService.stats("AAPL")).thenReturn(new SymbolStatsResponse("AAPL", Instant.now(), 3,
            new BigDecimal("10"), new BigDecimal("2304.60"), new BigDecimal("230.46"), new BigDecimal("230.00"),
            new BigDecimal("231.00"), new BigDecimal("230.00"), new BigDecimal("231.00"), new BigDecimal("0.43")));

        mockMvc.perform(get("/api/market/AAPL/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tradeCount").value(3))
            .andExpect(jsonPath("$.averagePrice").value(230.46))
            .andExpect(jsonPath("$.changePercent").value(0.43));
    }
}
