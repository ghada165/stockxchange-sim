package com.exchange.matching.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Transaction executee entre un ordre acheteur et un ordre vendeur.
 *
 * C'est un record: une donnee immuable par nature (un trade ne change
 * jamais une fois execute). Le record genere le constructeur, les
 * accesseurs (id(), price()...), equals(), hashCode() et toString().
 *
 * La methode {@link #execute} sert de fabrique: elle genere l'id et
 * l'horodatage, pour que le moteur n'ait a fournir que les donnees metier.
 */
public record Trade(
    String id,
    String symbol,
    String buyOrderId,
    String sellOrderId,
    String buyerId,
    String sellerId,
    BigDecimal price,
    BigDecimal quantity,
    Instant executedAt
) {

    public static Trade execute(String symbol, Order buyOrder, Order sellOrder,
                                BigDecimal price, BigDecimal quantity) {
        return new Trade(UUID.randomUUID().toString(), symbol,
            buyOrder.getId(), sellOrder.getId(), buyOrder.getUserId(), sellOrder.getUserId(),
            price, quantity, Instant.now());
    }

    /** Montant en cash de la transaction (prix x quantite). */
    public BigDecimal notional() {
        return price.multiply(quantity);
    }
}
