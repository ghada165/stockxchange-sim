package com.exchange.matching.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "username est requis") String username,
    @NotBlank(message = "password est requis") String password
) {}
