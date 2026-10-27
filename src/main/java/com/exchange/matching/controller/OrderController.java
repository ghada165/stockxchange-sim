package com.exchange.matching.controller;

import com.exchange.matching.dto.OrderResponse;
import com.exchange.matching.dto.PlaceOrderRequest;
import com.exchange.matching.dto.PlaceOrderResponse;
import com.exchange.matching.service.TradingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ordres de l'utilisateur: placement, annulation, historique.
 *
 * NOTE AUTH: l'identite de l'utilisateur est recue via l'en-tete X-User-Id
 * en attendant l'ajout de Spring Security + JWT. A remplacer par un
 * principal authentifie (@AuthenticationPrincipal): tel quel, un client
 * pourrait se faire passer pour n'importe quel utilisateur.
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
    public ResponseEntity<PlaceOrderResponse> placeOrder(
        @RequestHeader("X-User-Id") String userId,
        @Valid @RequestBody PlaceOrderRequest request
    ) {
        PlaceOrderResponse response = tradingService.placeOrder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** DELETE /api/orders/{orderId} : annuler un ordre encore actif. */
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
        @RequestHeader("X-User-Id") String userId,
        @PathVariable String orderId
    ) {
        tradingService.cancelOrder(userId, orderId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/orders/my : historique des ordres de l'utilisateur, du plus recent au plus ancien. */
    @GetMapping("/my")
    public List<OrderResponse> myOrders(@RequestHeader("X-User-Id") String userId) {
        return tradingService.getMyOrders(userId).stream()
            .map(OrderResponse::from)
            .toList();
    }
}
