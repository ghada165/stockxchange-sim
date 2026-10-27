package com.exchange.matching.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "displayName: 100 caracteres maximum") String displayName,
    @Email(message = "email invalide") @Size(max = 255, message = "email: 255 caracteres maximum") String email
) {}
