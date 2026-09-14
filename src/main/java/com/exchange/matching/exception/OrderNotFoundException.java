package com.exchange.matching.exception;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String orderId) {
        super("Ordre introuvable: " + orderId);
    }
}
