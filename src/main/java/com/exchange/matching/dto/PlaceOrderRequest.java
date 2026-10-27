package com.exchange.matching.dto;

import com.exchange.matching.model.OrderSide;
import com.exchange.matching.model.OrderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Requete de placement d'ordre.
 *
 * Prix au centime pres et quantite en actions entieres: prix x quantite a
 * donc au plus 2 decimales, ce qui tient sans arrondi dans les colonnes
 * NUMERIC(19,4) des wallets (aucune derive du solde bloque).
 */
public record PlaceOrderRequest(

    @NotBlank(message = "symbol est requis")
    String symbol,

    @NotNull(message = "side est requis (BUY ou SELL)")
    OrderSide side,

    @NotNull(message = "type est requis (LIMIT ou MARKET)")
    OrderType type,

    // null autorise uniquement si type == MARKET (verifie dans le service)
    @DecimalMin(value = "0.01", message = "price doit etre au moins 0.01")
    @Digits(integer = 13, fraction = 2, message = "price: 2 decimales maximum")
    BigDecimal price,

    @NotNull(message = "quantity est requis")
    @DecimalMin(value = "1", message = "quantity doit etre au moins 1")
    @Digits(integer = 11, fraction = 0, message = "quantity doit etre un nombre entier d'actions")
    BigDecimal quantity
) {}
