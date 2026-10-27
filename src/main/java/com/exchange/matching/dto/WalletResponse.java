package com.exchange.matching.dto;

import com.exchange.matching.persistence.entity.PositionEntity;
import com.exchange.matching.persistence.entity.WalletEntity;

import java.math.BigDecimal;
import java.util.List;

public record WalletResponse(
    BigDecimal balance,
    BigDecimal lockedBalance,
    BigDecimal availableBalance,
    List<PositionResponse> positions
) {

    public static WalletResponse of(WalletEntity wallet, List<PositionEntity> positions) {
        return new WalletResponse(
            wallet.getBalance(),
            wallet.getLockedBalance(),
            wallet.availableBalance(),
            positions.stream().map(PositionResponse::from).toList()
        );
    }
}
