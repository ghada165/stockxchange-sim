package com.exchange.matching.web;

import com.exchange.matching.service.TradingService;
import com.exchange.matching.web.dto.OrderResponse;
import com.exchange.matching.web.dto.PlaceOrderRequest;
import com.exchange.matching.web.dto.PlaceOrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * NOTE AUTH: l'identite de l'utilisateur est recue via l'en-tete X-User-Id
 * en attendant l'ajout de Spring Security + JWT (prevu plus tard dans le
 * planning du semestre). A remplacer par un principal authentifie
 * (@AuthenticationPrincipal) une fois la securite en place - ne pas
 * utiliser cet en-tete tel quel en production, un client pourrait usurper
 * n'importe quel utilisateur.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final TradingService tradingService;

    public OrderController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(
        @RequestHeader("X-User-Id") String userId,
        @Valid @RequestBody PlaceOrderRequest request
    ) {
        PlaceOrderResponse response = tradingService.placeOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
        @RequestHeader("X-User-Id") String userId,
        @RequestParam String symbol,
        @PathVariable String orderId
    ) {
        tradingService.cancelOrder(userId, symbol, orderId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> myOrders(@RequestHeader("X-User-Id") String userId) {
        List<OrderResponse> orders = tradingService.getMyOrders(userId).stream()
            .map(OrderResponse::from)
            .toList();
        return ResponseEntity.ok(orders);
    }
}
