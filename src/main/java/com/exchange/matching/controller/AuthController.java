package com.exchange.matching.controller;

import com.exchange.matching.config.OpenApiConfig;
import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.dto.ChangePasswordRequest;
import com.exchange.matching.dto.LoginRequest;
import com.exchange.matching.dto.RegisterRequest;
import com.exchange.matching.dto.UpdateProfileRequest;
import com.exchange.matching.dto.UserProfileResponse;
import com.exchange.matching.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Module Auth & Utilisateurs. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Inscription, connexion et profil")
public class AuthController {

    @Autowired
    private AuthService authService;

    /** POST /api/auth/register : inscription d'un trader (public). Renvoie directement un jeton. */
    @PostMapping("/register")
    @Operation(summary = "Inscription d'un trader", description = "Public. Cree le compte, le wallet de demo et renvoie un jeton JWT.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Compte cree"),
        @ApiResponse(responseCode = "400", description = "Requete invalide"),
        @ApiResponse(responseCode = "409", description = "Nom d'utilisateur deja pris")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** POST /api/auth/login : connexion (public). */
    @PostMapping("/login")
    @Operation(summary = "Connexion", description = "Public. Renvoie un jeton JWT a utiliser dans le bouton Authorize.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connecte"),
        @ApiResponse(responseCode = "401", description = "Identifiants incorrects"),
        @ApiResponse(responseCode = "403", description = "Compte suspendu")
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** POST /api/auth/logout : revoque le jeton courant. */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/auth/me : profil de l'utilisateur connecte. */
    @GetMapping("/me")
    @Operation(summary = "Mon profil")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @PreAuthorize("isAuthenticated()")
    public UserProfileResponse me(Authentication authentication) {
        return authService.profile(authentication.getName());
    }

    /** PUT /api/auth/me : modifier son nom affiche et son email. */
    @PutMapping("/me")
    @Operation(summary = "Modifier mon profil", description = "Nom affiche et email.")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @PreAuthorize("isAuthenticated()")
    public UserProfileResponse updateProfile(Authentication authentication,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(authentication.getName(), request);
    }

    /** PUT /api/auth/password : changer son mot de passe (invalide tous les autres jetons). */
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public AuthResponse changePassword(Authentication authentication,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(authentication.getName(), request);
    }
}
