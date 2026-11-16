package com.exchange.matching.exception;

/** Ressource introuvable (symbole, utilisateur...): traduite en HTTP 404 par GlobalExceptionHandler. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
