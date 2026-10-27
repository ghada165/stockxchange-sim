package com.exchange.matching.engine;

import com.exchange.matching.model.OrderStatus;
import com.exchange.matching.model.Trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultat de la soumission d'un ordre au moteur.
 *
 * {@code status} et {@code remainingQuantity} sont une PHOTO prise sous le
 * verrou du symbole, juste apres le matching. On ne relit pas l'objet Order
 * ensuite: s'il reste dans le carnet, un autre thread peut le modifier a
 * tout moment (nouvelle execution, annulation).
 */
public record MatchResult(
    List<Trade> trades,
    OrderStatus status,
    BigDecimal remainingQuantity
) {}
