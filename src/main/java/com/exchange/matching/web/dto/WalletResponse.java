package com.exchange.matching.web.dto;

import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.WalletEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record WalletResponse(
    BigDecimal balance,
    BigDecimal lockedBalance,
    BigDecimal availableBalance,
    Map<String, BigDecimal> positions // symbole -> quantite detenue
) {

    public static WalletResponse of(WalletEntity wallet, List<PositionEntity> positions) {
        return new WalletResponse(
            wallet.getBalance(),
            wallet.getLockedBalance(),
            wallet.availableBalance(),
            positions.stream()
                .collect(Collectors.toMap(PositionEntity::getSymbol, PositionEntity::getQuantity))
        );
    }
}
