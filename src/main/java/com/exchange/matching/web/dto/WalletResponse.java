package com.exchange.matching.web.dto;

import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.WalletEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WalletResponse {
    public BigDecimal balance;
    public BigDecimal lockedBalance;
    public BigDecimal availableBalance;
    public Map<String, BigDecimal> positions; // symbole -> quantite detenue

    public static WalletResponse of(WalletEntity wallet, List<PositionEntity> positions) {
        WalletResponse r = new WalletResponse();
        r.balance = wallet.getBalance();
        r.lockedBalance = wallet.getLockedBalance();
        r.availableBalance = wallet.availableBalance();
        r.positions = positions.stream()
            .collect(Collectors.toMap(PositionEntity::getSymbol, PositionEntity::getQuantity));
        return r;
    }
}
