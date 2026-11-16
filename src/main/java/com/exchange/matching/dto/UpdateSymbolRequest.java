package com.exchange.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Modification d'un symbole cote (administrateur): nom affiche et prix de reference. */
public record UpdateSymbolRequest(

    @Schema(description = "Nom de la societe", example = "Apple Inc.")
    @NotBlank(message = "name est requis")
    @Size(max = 100, message = "name: 100 caracteres maximum")
    String name,

    @Schema(description = "Prix de reference en $", example = "235.50")
    @NotNull(message = "referencePrice est requis")
    @DecimalMin(value = "0.01", message = "referencePrice doit etre au moins 0.01")
    @Digits(integer = 13, fraction = 2, message = "referencePrice: 2 decimales maximum")
    BigDecimal referencePrice
) {}
