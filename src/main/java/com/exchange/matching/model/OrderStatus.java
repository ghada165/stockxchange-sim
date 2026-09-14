package com.exchange.matching.model;

public enum OrderStatus {
    /** En attente dans le carnet, aucune execution. */
    OPEN,

    /** Partiellement execute, reste une quantite en attente. */
    PARTIALLY_FILLED,

    /** Entierement execute. */
    FILLED,

    /** Annule par l'utilisateur avant execution complete. */
    CANCELLED,

    /** Rejete a la validation (ex: solde insuffisant, marche ferme). */
    REJECTED
}
