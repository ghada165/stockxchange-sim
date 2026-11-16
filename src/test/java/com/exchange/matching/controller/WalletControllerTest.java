package com.exchange.matching.controller;

import com.exchange.matching.dto.PositionResponse;
import com.exchange.matching.dto.WalletResponse;
import com.exchange.matching.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@AutoConfigureMockMvc(addFilters = false)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingService tradingService;

    @Test
    void myWallet_shouldReturnBalancesAndPositions() throws Exception {
        when(tradingService.getWallet("alice")).thenReturn(new WalletResponse(
            new BigDecimal("100000"), new BigDecimal("2300"), new BigDecimal("97700"),
            List.of(new PositionResponse("AAPL", new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("95")))));

        mockMvc.perform(get("/api/wallet").principal(new UsernamePasswordAuthenticationToken("alice", null, List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(100000))
            .andExpect(jsonPath("$.lockedBalance").value(2300))
            .andExpect(jsonPath("$.availableBalance").value(97700))
            .andExpect(jsonPath("$.positions", hasSize(1)))
            .andExpect(jsonPath("$.positions[0].availableQuantity").value(95));

        verify(tradingService).getWallet("alice");
    }
}
