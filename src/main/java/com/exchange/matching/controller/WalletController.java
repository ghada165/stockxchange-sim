package com.exchange.matching.controller;

import com.exchange.matching.config.OpenApiConfig;
import com.exchange.matching.dto.WalletResponse;
import com.exchange.matching.service.TradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Module Wallet & Positions (role TRADER). */
@RestController
@RequestMapping("/api/wallet")
@Tag(name = "Wallet", description = "Solde et positions (role TRADER)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class WalletController {

    private final TradingService tradingService;

    public WalletController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /** GET /api/wallet : solde, solde bloque, solde disponible et positions. */
    @GetMapping
    @Operation(summary = "Mon wallet", description = "Solde, solde bloque, solde disponible et positions par symbole.")
    public WalletResponse myWallet(Authentication authentication) {
        return tradingService.getWallet(authentication.getName());
    }
}
