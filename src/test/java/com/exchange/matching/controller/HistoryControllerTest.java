package com.exchange.matching.controller;

import com.exchange.matching.dto.MyTradeResponse;
import com.exchange.matching.dto.TradeHistoryResponse;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class HistoryControllerTest {

    private static final Authentication ALICE = new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportingService reportingService;

    @Test
    void myTrades_shouldReturnStatementWithSummary() throws Exception {
        MyTradeResponse buy = new MyTradeResponse("t-1", "AAPL", OrderSide.BUY, new BigDecimal("230.00"),
            new BigDecimal("2"), new BigDecimal("460.00"), Instant.now());
        when(reportingService.history("alice")).thenReturn(new TradeHistoryResponse("alice", Instant.now(),
            new TradeHistoryResponse.Summary(1, 1, 0, new BigDecimal("460.00"), BigDecimal.ZERO, new BigDecimal("-460.00")),
            List.of(buy)));

        mockMvc.perform(get("/api/history/trades").principal(ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.summary.buyCount").value(1))
            .andExpect(jsonPath("$.summary.netCashFlow").value(-460.00))
            .andExpect(jsonPath("$.trades", hasSize(1)))
            .andExpect(jsonPath("$.trades[0].side").value("BUY"));
    }

    @Test
    void myTrades_whenNoTrade_shouldReturnEmptyStatement() throws Exception {
        when(reportingService.history("alice")).thenReturn(new TradeHistoryResponse("alice", Instant.now(),
            new TradeHistoryResponse.Summary(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), List.of()));

        mockMvc.perform(get("/api/history/trades").principal(ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.tradeCount").value(0))
            .andExpect(jsonPath("$.trades", hasSize(0)));
    }
}
