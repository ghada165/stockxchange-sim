package com.exchange.matching.controller;

import com.exchange.matching.dto.OrderResponse;
import com.exchange.matching.dto.PlaceOrderRequest;
import com.exchange.matching.dto.PlaceOrderResponse;
import com.exchange.matching.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module Ordres (role TRADER). L'utilisateur est celui du jeton JWT
 * (authentication.getName() = claim "sub"): impossible d'agir au nom d'un autre.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final TradingService tradingService;

    public OrderController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /** POST /api/orders : passer un ordre LIMIT ou MARKET. */
    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(Authentication authentication,
                                                         @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResponse response = tradingService.placeOrder(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** DELETE /api/orders/{orderId} : annuler un de ses ordres encore actif. */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(Authentication authentication, @PathVariable String orderId) {
        tradingService.cancelOrder(authentication.getName(), orderId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/orders/my : ses ordres (en cours et historique), du plus recent au plus ancien. */
    @GetMapping("/my")
    public List<OrderResponse> myOrders(Authentication authentication) {
        return tradingService.getMyOrders(authentication.getName()).stream()
            .map(OrderResponse::from)
            .toList();
    }
}
