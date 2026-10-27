package com.exchange.matching.exception;

/** Ordre refuse: marche ferme ou cotation suspendue (HTTP 409). */
public class MarketClosedException extends RuntimeException {
    public MarketClosedException(String message) {
        super(message);
    }
}
