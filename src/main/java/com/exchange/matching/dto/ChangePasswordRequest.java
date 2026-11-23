package com.exchange.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Changement de mot de passe: l'ancien est exige, le nouveau suit la politique de mot de passe. */
public record ChangePasswordRequest(

    @NotBlank(message = "currentPassword est requis")
    String currentPassword,

    @NotBlank(message = "newPassword est requis")
    @Size(min = 8, max = 72, message = "newPassword doit faire entre 8 et 72 caracteres")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    String newPassword
) {}
