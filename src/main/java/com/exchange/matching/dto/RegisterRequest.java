package com.exchange.matching.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Inscription d'un nouveau trader. */
public record RegisterRequest(

    @NotBlank(message = "username est requis")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,32}$",
        message = "username: 3 a 32 caracteres parmi lettres, chiffres, point, tiret, souligne")
    String username,

    @NotBlank(message = "password est requis")
    @Size(min = 8, max = 72, message = "password doit faire entre 8 et 72 caracteres")
    String password,

    @Size(max = 100, message = "displayName: 100 caracteres maximum")
    String displayName,

    @Email(message = "email invalide")
    @Size(max = 255, message = "email: 255 caracteres maximum")
    String email
) {}
