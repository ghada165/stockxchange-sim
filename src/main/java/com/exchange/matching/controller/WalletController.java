package com.exchange.matching.controller;

import com.exchange.matching.dto.WalletResponse;
import com.exchange.matching.service.TradingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Portefeuille de l'utilisateur: cash et positions. */
@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final TradingService tradingService;

    public WalletController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    /** GET /api/wallet : solde, solde bloque, solde disponible et positions. */
    @GetMapping
    public WalletResponse myWallet(@RequestHeader("X-User-Id") String userId) {
        return tradingService.getWallet(userId);
    }
}
