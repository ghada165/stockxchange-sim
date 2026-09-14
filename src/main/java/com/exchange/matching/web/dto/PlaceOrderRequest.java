package com.exchange.matching.web.dto;

import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaceOrderRequest(

    @NotBlank(message = "symbol est requis")
    String symbol,

    @NotNull(message = "side est requis (BUY ou SELL)")
    OrderSide side,

    @NotNull(message = "type est requis (LIMIT ou MARKET)")
    OrderType type,

    // null autorise uniquement si type == MARKET (valide dans le service)
    @DecimalMin(value = "0.0001", message = "price doit etre strictement positif")
    BigDecimal price,

    @NotNull(message = "quantity est requis")
    @DecimalMin(value = "0.00000001", message = "quantity doit etre strictement positif")
    BigDecimal quantity
) {}
