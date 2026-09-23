package com.exchange.matching.web;

import com.exchange.matching.service.TradingService;
import com.exchange.matching.web.dto.WalletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final TradingService tradingService;

    public WalletController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @GetMapping
    public WalletResponse myWallet(@RequestHeader("X-User-Id") String userId) {
        return tradingService.getWallet(userId);
    }
}
