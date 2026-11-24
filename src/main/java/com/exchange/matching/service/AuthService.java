package com.exchange.matching.service;

import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.dto.ChangePasswordRequest;
import com.exchange.matching.dto.LoginRequest;
import com.exchange.matching.dto.RegisterRequest;
import com.exchange.matching.dto.UpdateProfileRequest;
import com.exchange.matching.dto.UserProfileResponse;
import com.exchange.matching.exception.AccountLockedException;
import com.exchange.matching.exception.AuthenticationFailedException;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.RevokedTokenEntity;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.repository.RevokedTokenRepository;
import com.exchange.matching.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Module Auth & Utilisateurs: inscription, connexion, deconnexion, profil,
 * changement de mot de passe.
 *
 * La connexion suit la demarche JWT du cours: l'AuthenticationManager verifie
 * login et mot de passe (via CustomUserDetailsService + BCrypt), puis
 * TokenService prepare et signe le jeton.
 */
@Service
public class AuthService {

    /** Noms reserves au systeme: personne ne peut s'inscrire sous ces identites. */
    private static final Set<String> RESERVED_USERNAMES = Set.of(TradingService.MARKET_MAKER, "admin", "system");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RevokedTokenRepository revokedTokens;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private TradingService tradingService;
    @Autowired
    private ActivityLogService activityLog;
    @Autowired
    private ExchangeProperties properties;

    /** Inscription d'un TRADER (le role ADMIN ne s'obtient que par un administrateur). */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalize(request.username());
        if (RESERVED_USERNAMES.contains(username) || userRepository.existsByUsername(username)) {
            throw new IllegalStateException("Le nom d'utilisateur '" + username + "' est deja pris");
        }
        UserEntity user = userRepository.save(new UserEntity(username, passwordEncoder.encode(request.password()),
            blankToNull(request.displayName()), blankToNull(request.email()), Role.TRADER));
        tradingService.openAccount(username);
        activityLog.record(username, ActivityType.USER_REGISTERED, "Inscription d'un trader");
        return tokenService.issue(user);
    }

    /**
     * Connexion. Pas de @Transactional: un echec leve une exception, qui annulerait
     * aussi l'enregistrement de la tentative (journal, compteur d'echecs).
     *
     * Ordre des controles:
     *   1. compte verrouille ?            -> 423 (LockedException de Spring)
     *   2. login et mot de passe valides ? -> 401, et l'echec est compte
     *   3. compte suspendu ?              -> 403 (seulement si le mot de passe est bon)
     */
    public AuthResponse login(LoginRequest request) {
        String username = normalize(request.username());
        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (LockedException e) {
            activityLog.record(username, ActivityType.LOGIN_FAILED, "Compte verrouille");
            throw new AccountLockedException("Compte temporairement verrouille apres "
                + properties.security().maxFailedLogins() + " echecs. Reessayez dans "
                + properties.security().lockDuration().toMinutes() + " minutes.");
        } catch (BadCredentialsException e) {
            // Inconnu ou mauvais mot de passe: meme message, pour ne pas reveler quels comptes existent.
            userRepository.findByUsername(username).ifPresent(this::countFailedLogin);
            activityLog.record(username, ActivityType.LOGIN_FAILED, "Identifiants invalides");
            throw new AuthenticationFailedException("Nom d'utilisateur ou mot de passe incorrect");
        }

        UserEntity user = userRepository.findByUsername(username).orElseThrow();
        if (!user.isActive()) {
            activityLog.record(username, ActivityType.LOGIN_FAILED, "Compte suspendu");
            throw new ForbiddenOperationException("Ce compte est suspendu. Contactez un administrateur.");
        }
        user.registerSuccessfulLogin();
        userRepository.save(user);
        activityLog.record(username, ActivityType.LOGIN_SUCCESS, "Connexion (" + user.getRole() + ")");
        return tokenService.issue(user);
    }

    private void countFailedLogin(UserEntity user) {
        Duration lockDuration = properties.security().lockDuration();
        boolean nowLocked = user.registerFailedLogin(properties.security().maxFailedLogins(), lockDuration, Instant.now());
        userRepository.save(user);
        if (nowLocked) {
            activityLog.record(user.getUsername(), ActivityType.ACCOUNT_LOCKED,
                "Verrouille " + lockDuration.toMinutes() + " min apres " + properties.security().maxFailedLogins()
                    + " echecs consecutifs");
        }
    }

    /**
     * Deconnexion: le jeton courant est ajoute a la liste de revocation. Il sera
     * refuse par TokenRevocationValidator jusqu'a son expiration naturelle.
     */
    @Transactional
    public void logout(Jwt jwt) {
        if (jwt.getId() != null && !revokedTokens.existsById(jwt.getId())) {
            revokedTokens.save(new RevokedTokenEntity(jwt.getId(), jwt.getSubject(), jwt.getExpiresAt()));
        }
        activityLog.record(jwt.getSubject(), ActivityType.LOGOUT, "Deconnexion");
    }

    /**
     * Changement de mot de passe: l'ancien est reverifie par l'AuthenticationManager,
     * puis TOUS les jetons existants du compte sont invalides (sessions ouvertes
     * ailleurs comprises). Un nouveau jeton est renvoye pour rester connecte ici.
     */
    @Transactional
    public AuthResponse changePassword(String username, ChangePasswordRequest request) {
        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, request.currentPassword()));
        } catch (BadCredentialsException | LockedException e) {
            throw new AuthenticationFailedException("Mot de passe actuel incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("Le nouveau mot de passe doit etre different de l'ancien");
        }
        UserEntity user = requireUser(username);
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        activityLog.record(username, ActivityType.PASSWORD_CHANGED, "Mot de passe modifie, jetons existants invalides");
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(String username) {
        return UserProfileResponse.from(requireUser(username));
    }

    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        UserEntity user = requireUser(username);
        user.setDisplayName(blankToNull(request.displayName()));
        user.setEmail(blankToNull(request.email()));
        activityLog.record(username, ActivityType.PROFILE_UPDATED, "Profil mis a jour");
        return UserProfileResponse.from(user);
    }

    private UserEntity requireUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new AuthenticationFailedException("Utilisateur introuvable: reconnectez-vous"));
    }

    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
