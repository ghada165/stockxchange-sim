package com.exchange.matching.service;

import com.exchange.matching.TestInjection;
import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.config.SecurityConfig;
import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.exchange.matching.security.TokenRevocationValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TokenService est teste avec un vrai encodeur JWT (HMAC-SHA256): le jeton
 * produit est relu par le decodeur de SecurityConfig, comme le ferait l'API.
 */
class TokenServiceTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghij";

    private final SecurityConfig securityConfig = new SecurityConfig();
    private TokenService tokenService;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        ExchangeProperties properties = new ExchangeProperties(null, null, null, false, false, null,
            new ExchangeProperties.Security(SECRET, Duration.ofHours(2), "admin123", null, null));
        SecretKey key = securityConfig.jwtSigningKey(properties);
        tokenService = TestInjection.inject(new TokenService(), securityConfig.jwtEncoder(key), properties);
        decoder = securityConfig.jwtDecoder(key, acceptAll());
    }

    /**
     * Validateur de revocation qui accepte tout: ce test porte sur la signature
     * et le contenu du jeton, pas sur la base (deconnexion, suspension).
     */
    private static TokenRevocationValidator acceptAll() {
        TokenRevocationValidator validator = mock(TokenRevocationValidator.class);
        when(validator.validate(any())).thenReturn(OAuth2TokenValidatorResult.success());
        return validator;
    }

    @Test
    void issue_shouldCreateSignedTokenWithUsernameRoleAndExpiry() {
        UserEntity alice = new UserEntity("alice", "hash", "Alice", null, Role.TRADER);

        AuthResponse response = tokenService.issue(alice);

        Jwt jwt = decoder.decode(response.token());
        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("TRADER"));
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("stockxchange-sim");
        assertThat(jwt.getExpiresAt()).isBetween(Instant.now().plus(Duration.ofMinutes(119)), Instant.now().plus(Duration.ofMinutes(121)));
        // le JWT stocke l'expiration a la seconde pres
        assertThat(response.expiresAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(jwt.getExpiresAt());
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().displayName()).isEqualTo("Alice");
    }

    @Test
    void issue_forAdmin_shouldCarryAdminRole() {
        AuthResponse response = tokenService.issue(new UserEntity("admin", "hash", null, null, Role.ADMIN));

        assertThat(decoder.decode(response.token()).getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    @Test
    void tokenSignedWithAnotherKey_shouldBeRejected() {
        ExchangeProperties other = new ExchangeProperties(null, null, null, false, false, null,
            new ExchangeProperties.Security("another-secret-0123456789-abcdefghijkl", null, null, null, null));
        SecretKey otherKey = securityConfig.jwtSigningKey(other);
        String forged = TestInjection.inject(new TokenService(), securityConfig.jwtEncoder(otherKey), other)
            .issue(new UserEntity("mallory", "hash", null, null, Role.ADMIN)).token();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }
}
