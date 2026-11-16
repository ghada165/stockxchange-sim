package com.exchange.matching.controller;

import com.exchange.matching.config.OpenApiConfig;
import com.exchange.matching.dto.OrderResponse;
import com.exchange.matching.dto.PlaceOrderRequest;
import com.exchange.matching.dto.PlaceOrderResponse;
import com.exchange.matching.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Ordres", description = "Passer, annuler et consulter ses ordres (role TRADER)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class OrderController {

    private final TradingService tradingService;

    public OrderController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /** POST /api/orders : passer un ordre LIMIT ou MARKET. */
    @PostMapping
    @Operation(summary = "Passer un ordre", description = "LIMIT (avec prix) ou MARKET (sans prix). Les fonds (achat) "
        + "ou les actions (vente) sont bloques avant le matching.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Ordre accepte (eventuellement deja execute)"),
        @ApiResponse(responseCode = "400", description = "Requete invalide ou symbole inconnu"),
        @ApiResponse(responseCode = "403", description = "Compte suspendu"),
        @ApiResponse(responseCode = "409", description = "Marche ferme ou symbole suspendu"),
        @ApiResponse(responseCode = "422", description = "Fonds ou actions insuffisants")
    })
    public ResponseEntity<PlaceOrderResponse> placeOrder(Authentication authentication,
                                                         @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResponse response = tradingService.placeOrder(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** DELETE /api/orders/{orderId} : annuler un de ses ordres encore actif. */
    @DeleteMapping("/{orderId}")
    @Operation(summary = "Annuler un ordre actif", description = "Libere la partie encore bloquee des fonds ou des actions.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Ordre annule"),
        @ApiResponse(responseCode = "404", description = "Ordre introuvable, deja termine ou appartenant a un autre utilisateur")
    })
    public ResponseEntity<Void> cancelOrder(Authentication authentication,
                                            @Parameter(description = "Identifiant de l'ordre") @PathVariable String orderId) {
        tradingService.cancelOrder(authentication.getName(), orderId);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/orders/my : ses ordres (en cours et historique), du plus recent au plus ancien. */
    @GetMapping("/my")
    @Operation(summary = "Mes ordres", description = "En cours et historique, du plus recent au plus ancien.")
    public List<OrderResponse> myOrders(Authentication authentication) {
        return tradingService.getMyOrders(authentication.getName()).stream()
            .map(OrderResponse::from)
            .toList();
    }
}
