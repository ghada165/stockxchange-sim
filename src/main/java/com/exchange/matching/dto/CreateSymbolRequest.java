package com.exchange.matching.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Introduction en bourse d'un nouveau symbole (administrateur). */
public record CreateSymbolRequest(

    @NotBlank(message = "symbol est requis")
    @Pattern(regexp = "^[A-Z]{1,10}$", message = "symbol: 1 a 10 lettres majuscules")
    String symbol,

    @NotBlank(message = "name est requis")
    @Size(max = 100, message = "name: 100 caracteres maximum")
    String name,

    @NotNull(message = "referencePrice est requis")
    @DecimalMin(value = "0.01", message = "referencePrice doit etre au moins 0.01")
    @Digits(integer = 13, fraction = 2, message = "referencePrice: 2 decimales maximum")
    BigDecimal referencePrice
) {}
