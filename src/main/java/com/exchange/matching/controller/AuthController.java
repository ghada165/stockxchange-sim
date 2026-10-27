package com.exchange.matching.controller;

import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.dto.LoginRequest;
import com.exchange.matching.dto.RegisterRequest;
import com.exchange.matching.dto.UpdateProfileRequest;
import com.exchange.matching.dto.UserProfileResponse;
import com.exchange.matching.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Module Auth & Utilisateurs. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /api/auth/register : inscription d'un trader (public). Renvoie directement un jeton. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** POST /api/auth/login : connexion (public). */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** GET /api/auth/me : profil de l'utilisateur connecte. */
    @GetMapping("/me")
    public UserProfileResponse me(Authentication authentication) {
        return authService.profile(authentication.getName());
    }

    /** PUT /api/auth/me : modifier son nom affiche et son email. */
    @PutMapping("/me")
    public UserProfileResponse updateProfile(Authentication authentication,
                                             @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(authentication.getName(), request);
    }
}
