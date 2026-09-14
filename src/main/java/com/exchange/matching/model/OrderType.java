package com.exchange.matching.model;

public enum OrderType {
    /**
     * Ordre a cours limite: prix maximum (achat) ou minimum (vente) accepte.
     * Reste dans le carnet tant qu'il n'est pas execute.
     */
    LIMIT,

    /**
     * Ordre au marche: execute immediatement au meilleur prix disponible,
     * sans limite de prix. N'entre jamais dans le carnet.
     */
    MARKET
}
