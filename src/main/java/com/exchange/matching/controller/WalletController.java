package com.exchange.matching.controller;

import com.exchange.matching.dto.WalletResponse;
import com.exchange.matching.service.TradingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Module Wallet & Positions (role TRADER). */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final TradingService tradingService;

    public WalletController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /** GET /api/wallet : solde, solde bloque, solde disponible et positions. */
    @GetMapping
    public WalletResponse myWallet(Authentication authentication) {
        return tradingService.getWallet(authentication.getName());
    }
}
