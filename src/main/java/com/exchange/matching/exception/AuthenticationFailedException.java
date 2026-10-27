package com.exchange.matching.exception;

/** Identifiants invalides (HTTP 401). */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
