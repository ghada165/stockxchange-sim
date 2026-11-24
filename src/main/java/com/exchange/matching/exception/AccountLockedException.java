package com.exchange.matching.exception;

/** Compte temporairement verrouille apres trop d'echecs de connexion (HTTP 423). */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
