package com.exchange.matching.model;

/** Etat d'un compte utilisateur ou d'un symbole cote. */
public enum AccountStatus {
    ACTIVE,
    /** Suspendu par un administrateur: plus de nouvel ordre possible. */
    SUSPENDED
}
