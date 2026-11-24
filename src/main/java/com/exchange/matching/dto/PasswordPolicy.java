package com.exchange.matching.dto;

/**
 * Politique de mot de passe commune a l'inscription et au changement de mot de passe:
 * au moins une lettre et un chiffre (la longueur 8-72 est verifiee par @Size;
 * 72 octets est la limite de BCrypt).
 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String MESSAGE = "le mot de passe doit contenir au moins une lettre et un chiffre";

    private PasswordPolicy() {
    }
}
