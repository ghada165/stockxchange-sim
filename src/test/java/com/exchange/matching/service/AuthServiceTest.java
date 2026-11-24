package com.exchange.matching.service;

import com.exchange.matching.TestInjection;
import com.exchange.matching.config.ExchangeProperties;
import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.dto.ChangePasswordRequest;
import com.exchange.matching.exception.AccountLockedException;
import com.exchange.matching.persistence.entity.RevokedTokenEntity;
import com.exchange.matching.persistence.repository.RevokedTokenRepository;
import com.exchange.matching.dto.LoginRequest;
import com.exchange.matching.dto.RegisterRequest;
import com.exchange.matching.dto.UpdateProfileRequest;
import com.exchange.matching.dto.UserProfileResponse;
import com.exchange.matching.exception.AuthenticationFailedException;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private TradingService tradingService;
    @Mock private ActivityLogService activityLog;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RevokedTokenRepository revokedTokens;

    @InjectMocks
    private AuthService authService;

    /** Configuration reelle (5 echecs, 15 min): @InjectMocks n'injecte que les mocks. */
    @BeforeEach
    void setUp() {
        TestInjection.inject(authService, new ExchangeProperties(null, null, null, false, false, null, null));
    }

    private static AuthResponse token(UserEntity user) {
        return new AuthResponse("jwt", Instant.now(), UserProfileResponse.from(user));
    }

    // ---------- Inscription ----------

    @Test
    void register_shouldNormalizeEncodeSaveOpenAccountAndIssueToken() {
        when(userRepository.existsByUsername("dave")).thenReturn(false);
        when(passwordEncoder.encode("motdepasse1")).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(tokenService.issue(any(UserEntity.class))).thenAnswer(i -> token(i.getArgument(0)));

        AuthResponse response = authService.register(new RegisterRequest("  Dave ", "motdepasse1", " ", "dave@mail.com"));

        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(saved.capture());
        UserEntity user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("dave");           // minuscules, sans espaces
        assertThat(user.getPasswordHash()).isEqualTo("hashed");     // jamais le mot de passe en clair
        assertThat(user.getDisplayName()).isNull();                 // chaine vide -> null
        assertThat(user.getRole()).isEqualTo(Role.TRADER);
        verify(tradingService).openAccount("dave");
        verify(activityLog).record(eq("dave"), eq(ActivityType.USER_REGISTERED), anyString());
        assertThat(response.token()).isEqualTo("jwt");
    }

    @Test
    void register_withTakenUsername_shouldThrowAndSaveNothing() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "motdepasse1", null, null)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("deja pris");

        verify(userRepository, never()).save(any());
        verify(tradingService, never()).openAccount(anyString());
    }

    @Test
    void register_withReservedUsername_shouldBeRefused() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Admin", "motdepasse1", null, null)))
            .isInstanceOf(IllegalStateException.class);

        verify(userRepository, never()).save(any());
    }

    // ---------- Connexion (via l'AuthenticationManager, comme dans le cours) ----------

    @Test
    void login_withValidCredentials_shouldIssueTokenAndLogSuccess() {
        UserEntity alice = new UserEntity("alice", "hash", null, null, Role.TRADER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(tokenService.issue(alice)).thenReturn(token(alice));

        AuthResponse response = authService.login(new LoginRequest("ALICE", "password123"));

        ArgumentCaptor<UsernamePasswordAuthenticationToken> sent = ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(sent.capture());
        assertThat(sent.getValue().getName()).isEqualTo("alice"); // nom normalise avant verification
        assertThat(response.user().username()).isEqualTo("alice");
        verify(activityLog).record(eq("alice"), eq(ActivityType.LOGIN_SUCCESS), anyString());
    }

    @Test
    void login_withWrongPassword_shouldThrowCountFailureAndLog() {
        UserEntity alice = new UserEntity("alice", "hash", null, null, Role.TRADER);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
            .isInstanceOf(AuthenticationFailedException.class);

        assertThat(alice.getFailedLoginAttempts()).isEqualTo(1);
        verify(activityLog).record("alice", ActivityType.LOGIN_FAILED, "Identifiants invalides");
        verify(tokenService, never()).issue(any());
    }

    @Test
    void login_withUnknownUser_shouldThrowSameErrorAsWrongPassword() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "x")))
            .isInstanceOf(AuthenticationFailedException.class)
            .hasMessage("Nom d'utilisateur ou mot de passe incorrect");
    }

    @Test
    void login_fifthFailure_shouldLockTheAccount() {
        UserEntity alice = new UserEntity("alice", "hash", null, null, Role.TRADER);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(AuthenticationFailedException.class);
        }

        assertThat(alice.isLocked(Instant.now())).isTrue();
        verify(activityLog).record(eq("alice"), eq(ActivityType.ACCOUNT_LOCKED), anyString());
    }

    @Test
    void login_onLockedAccount_shouldThrowLocked() {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "password123")))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("15 minutes");
        verify(tokenService, never()).issue(any());
    }

    @Test
    void login_withSuspendedAccount_shouldThrowForbidden() {
        UserEntity eve = new UserEntity("eve", "hash", null, null, Role.TRADER);
        eve.setStatus(AccountStatus.SUSPENDED);
        when(userRepository.findByUsername("eve")).thenReturn(Optional.of(eve));

        assertThatThrownBy(() -> authService.login(new LoginRequest("eve", "motdepasse1")))
            .isInstanceOf(ForbiddenOperationException.class);

        verify(activityLog).record("eve", ActivityType.LOGIN_FAILED, "Compte suspendu");
        verify(tokenService, never()).issue(any());
    }

    // ---------- Deconnexion et mot de passe ----------

    @Test
    void logout_shouldRevokeTheCurrentToken() {
        Instant expiry = Instant.now().plusSeconds(3600);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("alice").jti("jti-1").expiresAt(expiry).build();
        when(revokedTokens.existsById("jti-1")).thenReturn(false);

        authService.logout(jwt);

        ArgumentCaptor<RevokedTokenEntity> revoked = ArgumentCaptor.forClass(RevokedTokenEntity.class);
        verify(revokedTokens).save(revoked.capture());
        assertThat(revoked.getValue().getJti()).isEqualTo("jti-1");
        assertThat(revoked.getValue().getExpiresAt()).isEqualTo(expiry);
        verify(activityLog).record("alice", ActivityType.LOGOUT, "Deconnexion");
    }

    @Test
    void changePassword_shouldHashNewPasswordAndInvalidateOldTokens() {
        UserEntity alice = new UserEntity("alice", "old-hash", null, null, Role.TRADER);
        int versionBefore = alice.getTokenVersion();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(passwordEncoder.encode("nouveau2026")).thenReturn("new-hash");
        when(tokenService.issue(alice)).thenReturn(token(alice));

        authService.changePassword("alice", new ChangePasswordRequest("motdepasse1", "nouveau2026"));

        assertThat(alice.getPasswordHash()).isEqualTo("new-hash");
        assertThat(alice.getTokenVersion()).isEqualTo(versionBefore + 1);
        verify(activityLog).record(eq("alice"), eq(ActivityType.PASSWORD_CHANGED), anyString());
    }

    @Test
    void changePassword_withWrongCurrentPassword_shouldChangeNothing() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.changePassword("alice", new ChangePasswordRequest("faux", "nouveau2026")))
            .isInstanceOf(AuthenticationFailedException.class);

        verify(passwordEncoder, never()).encode(anyString());
    }

    // ---------- Profil ----------

    @Test
    void profile_whenUserExists_shouldReturnIt() {
        when(userRepository.findByUsername("alice"))
            .thenReturn(Optional.of(new UserEntity("alice", "hash", "Alice", "alice@mail.com", Role.TRADER)));

        UserProfileResponse profile = authService.profile("alice");

        assertThat(profile.displayName()).isEqualTo("Alice");
        assertThat(profile.email()).isEqualTo("alice@mail.com");
    }

    @Test
    void profile_whenUserMissing_shouldThrow() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.profile("ghost")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void updateProfile_shouldModifyUserAndLog() {
        UserEntity alice = new UserEntity("alice", "hash", "Alice", "old@mail.com", Role.TRADER);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        UserProfileResponse updated = authService.updateProfile("alice", new UpdateProfileRequest(" Alice B. ", ""));

        assertThat(alice.getDisplayName()).isEqualTo("Alice B.");
        assertThat(alice.getEmail()).isNull();
        assertThat(updated.displayName()).isEqualTo("Alice B.");
        verify(activityLog).record(eq("alice"), eq(ActivityType.PROFILE_UPDATED), anyString());
    }
}
