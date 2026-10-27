package com.exchange.matching.service;

import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.dto.LoginRequest;
import com.exchange.matching.dto.RegisterRequest;
import com.exchange.matching.dto.UpdateProfileRequest;
import com.exchange.matching.dto.UserProfileResponse;
import com.exchange.matching.exception.AuthenticationFailedException;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** Module Auth & Utilisateurs: inscription, connexion, profil. */
@Service
public class AuthService {

    /** Noms reserves au systeme: personne ne peut s'inscrire sous ces identites. */
    private static final Set<String> RESERVED_USERNAMES = Set.of(TradingService.MARKET_MAKER, "admin", "system");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TradingService tradingService;
    private final ActivityLogService activityLog;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService,
                       TradingService tradingService, ActivityLogService activityLog) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tradingService = tradingService;
        this.activityLog = activityLog;
    }

    /** Inscription d'un TRADER: compte utilisateur + wallet et positions de demo. */
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
     * Pas de @Transactional ici: un echec de connexion leve une exception,
     * qui annulerait aussi l'enregistrement de la tentative dans le journal.
     */
    public AuthResponse login(LoginRequest request) {
        String username = normalize(request.username());
        UserEntity user = userRepository.findByUsername(username)
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .orElse(null);
        if (user == null) {
            activityLog.record(username, ActivityType.LOGIN_FAILED, "Identifiants invalides");
            throw new AuthenticationFailedException("Nom d'utilisateur ou mot de passe incorrect");
        }
        if (!user.isActive()) {
            activityLog.record(username, ActivityType.LOGIN_FAILED, "Compte suspendu");
            throw new ForbiddenOperationException("Ce compte est suspendu. Contactez un administrateur.");
        }
        activityLog.record(username, ActivityType.LOGIN_SUCCESS, "Connexion (" + user.getRole() + ")");
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
