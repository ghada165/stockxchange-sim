package com.exchange.matching.controller;

import com.exchange.matching.config.OpenApiConfig;
import com.exchange.matching.dto.AuthResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Module Auth & Utilisateurs. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Inscription, connexion et profil")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

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

    /** GET /api/auth/me : profil de l'utilisateur connecte. */
    @GetMapping("/me")
    @Operation(summary = "Mon profil")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    public UserProfileResponse me(Authentication authentication) {
        return authService.profile(authentication.getName());
    }

    /** PUT /api/auth/me : modifier son nom affiche et son email. */
    @PutMapping("/me")
    @Operation(summary = "Modifier mon profil", description = "Nom affiche et email.")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    public UserProfileResponse updateProfile(Authentication authentication,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(authentication.getName(), request);
    }
}
