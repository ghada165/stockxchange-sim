package com.exchange.matching.model;

/** Role d'un utilisateur authentifie (le Visiteur, lui, n'a pas de compte). */
public enum Role {
    /** Place et gere ses ordres, consulte son wallet et son historique. */
    TRADER,
    /** Supervise la plateforme: symboles, utilisateurs, heures de marche, logs. */
    ADMIN
}
