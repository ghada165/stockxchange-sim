package com.exchange.matching.controller;

import com.exchange.matching.dto.OrderResponse;
import com.exchange.matching.dto.PlaceOrderRequest;
import com.exchange.matching.dto.PlaceOrderResponse;
import com.exchange.matching.dto.TradeResponse;
import com.exchange.matching.exception.InsufficientFundsException;
import com.exchange.matching.exception.MarketClosedException;
import com.exchange.matching.exception.OrderNotFoundException;
import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.OrderType;
import com.exchange.matching.persistence.entity.OrderEntity;
import com.exchange.matching.service.TradingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final Authentication ALICE = new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingService tradingService;

    @Test
    void placeOrder_shouldReturn201WithOrderAndTrades() throws Exception {
        OrderResponse order = new OrderResponse("o-1", "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("231.00"),
            new BigDecimal("10"), new BigDecimal("4"), OrderStatus.PARTIALLY_FILLED, Instant.now());
        TradeResponse trade = new TradeResponse("t-1", "AAPL", new BigDecimal("230.46"), new BigDecimal("6"), Instant.now());
        when(tradingService.placeOrder(eq("alice"), any(PlaceOrderRequest.class)))
            .thenReturn(new PlaceOrderResponse(order, List.of(trade)));

        mockMvc.perform(post("/api/orders").principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":231.00,\"quantity\":10}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.order.id").value("o-1"))
            .andExpect(jsonPath("$.order.status").value("PARTIALLY_FILLED"))
            .andExpect(jsonPath("$.order.remainingQuantity").value(4))
            .andExpect(jsonPath("$.trades", hasSize(1)))
            .andExpect(jsonPath("$.trades[0].price").value(230.46));

        // l'utilisateur vient du jeton, jamais du corps de la requete
        verify(tradingService).placeOrder("alice", new PlaceOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT,
            new BigDecimal("231.00"), new BigDecimal("10")));
    }

    @Test
    void placeOrder_withoutSide_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/orders").principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"type\":\"MARKET\",\"quantity\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("side: side est requis (BUY ou SELL)"));

        verify(tradingService, never()).placeOrder(any(), any());
    }

    @Test
    void placeOrder_withFractionalQuantity_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/orders").principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1.5}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void placeOrder_withInsufficientFunds_shouldReturn422() throws Exception {
        when(tradingService.placeOrder(eq("alice"), any())).thenThrow(new InsufficientFundsException("Solde disponible insuffisant"));

        mockMvc.perform(post("/api/orders").principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":230.00,\"quantity\":100000}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Solde disponible insuffisant"));
    }

    @Test
    void placeOrder_whenMarketClosed_shouldReturn409() throws Exception {
        when(tradingService.placeOrder(eq("alice"), any())).thenThrow(new MarketClosedException("Marche ferme"));

        mockMvc.perform(post("/api/orders").principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":230.00,\"quantity\":1}"))
            .andExpect(status().isConflict());
    }

    @Test
    void cancelOrder_shouldReturn204AndCallService() throws Exception {
        doNothing().when(tradingService).cancelOrder("alice", "o-1");

        mockMvc.perform(delete("/api/orders/o-1").principal(ALICE))
            .andExpect(status().isNoContent());

        verify(tradingService, times(1)).cancelOrder("alice", "o-1");
    }

    @Test
    void cancelOrder_whenNotFound_shouldReturn404() throws Exception {
        doThrow(new OrderNotFoundException("o-404")).when(tradingService).cancelOrder("alice", "o-404");

        mockMvc.perform(delete("/api/orders/o-404").principal(ALICE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Ordre introuvable: o-404"));
    }

    @Test
    void myOrders_shouldReturn200AndOrdersOfConnectedUser() throws Exception {
        OrderEntity open = new OrderEntity("o-2", "alice", "TSLA", OrderSide.SELL, OrderType.LIMIT, new BigDecimal("260.00"),
            new BigDecimal("260.00"), new BigDecimal("5"), new BigDecimal("5"), OrderStatus.OPEN, Instant.now(), 2);
        OrderEntity filled = new OrderEntity("o-1", "alice", "AAPL", OrderSide.BUY, OrderType.MARKET, null,
            new BigDecimal("230.46"), new BigDecimal("1"), BigDecimal.ZERO, OrderStatus.FILLED, Instant.now(), 1);
        when(tradingService.getMyOrders("alice")).thenReturn(List.of(open, filled));

        mockMvc.perform(get("/api/orders/my").principal(ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value("o-2"))
            .andExpect(jsonPath("$[0].status").value("OPEN"))
            .andExpect(jsonPath("$[1].type").value("MARKET"))
            .andExpect(jsonPath("$[1].price").doesNotExist());
    }

    @Test
    void myOrders_whenNone_shouldReturnEmptyArray() throws Exception {
        when(tradingService.getMyOrders("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/orders/my").principal(ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
