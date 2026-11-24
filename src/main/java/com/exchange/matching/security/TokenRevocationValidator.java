package com.exchange.matching.security;

import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.repository.RevokedTokenRepository;
import com.exchange.matching.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validation supplementaire de chaque JWT recu, en plus de la signature et de
 * l'expiration (verifiees par Spring). Un jeton est refuse si:
 *   1. il a ete revoque par une deconnexion (son "jti" est dans revoked_tokens),
 *   2. son utilisateur n'existe plus ou est suspendu,
 *   3. sa version ("ver") n'est plus celle du compte: mot de passe ou role
 *      modifie, ou compte suspendu depuis son emission.
 *
 * C'est ce qui rend la deconnexion et la suspension effectives immediatement,
 * alors qu'un JWT est par nature valide jusqu'a son expiration.
 */
@Component
public class TokenRevocationValidator implements OAuth2TokenValidator<Jwt> {

    /** Claim portant la version des jetons du compte (voir UserEntity.tokenVersion). */
    public static final String TOKEN_VERSION_CLAIM = "ver";

    @Autowired
    private RevokedTokenRepository revokedTokens;
    @Autowired
    private UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getId() == null || revokedTokens.existsById(jwt.getId())) {
            return failure("Jeton revoque: reconnectez-vous");
        }
        Optional<UserEntity> user = userRepository.findByUsername(jwt.getSubject());
        if (user.isEmpty() || !user.get().isActive()) {
            return failure("Compte inexistant ou suspendu");
        }
        Object version = jwt.getClaim(TOKEN_VERSION_CLAIM);
        if (!(version instanceof Number number) || number.intValue() != user.get().getTokenVersion()) {
            return failure("Jeton perime (mot de passe ou role modifie): reconnectez-vous");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
