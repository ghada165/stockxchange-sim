package com.exchange.matching.exception;

/** Operation refusee a cet utilisateur: compte suspendu, action interdite (HTTP 403). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
