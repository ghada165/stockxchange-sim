package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.dto.UserProfileResponse;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.security.TokenRevocationValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Emet les jetons JWT. Contenu du jeton:
 *   jti   = identifiant unique du jeton (sert a le revoquer a la deconnexion)
 *   sub   = nom d'utilisateur (identifiant utilise partout dans l'API)
 *   roles = [TRADER] ou [ADMIN]
 *   exp   = date d'expiration
 *   ver   = version des jetons du compte (sert a invalider tous ses jetons d'un coup)
 * Signe en HMAC-SHA256 avec la cle de exchange.security.jwt-secret. Signe ne veut pas dire
 * chiffre: le contenu d'un JWT est lisible par tous, il ne contient donc aucun secret.
 */
@Service
public class TokenService {

    private static final String ISSUER = "stockxchange-sim";

    private final JwtEncoder jwtEncoder;
    private final ExchangeProperties properties;

    public TokenService(JwtEncoder jwtEncoder, ExchangeProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    public AuthResponse issue(UserEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.security().tokenValidity());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .id(UUID.randomUUID().toString())
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getUsername())
            .claim("roles", List.of(user.getRole().name()))
            .claim(TokenRevocationValidator.TOKEN_VERSION_CLAIM, user.getTokenVersion())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, expiresAt, UserProfileResponse.from(user));
    }
}
