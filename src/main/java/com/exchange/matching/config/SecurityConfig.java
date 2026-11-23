package com.exchange.matching.config;

import com.exchange.matching.security.CustomUserDetailsService;
import com.exchange.matching.security.JsonSecurityErrorHandler;
import com.exchange.matching.security.TokenRevocationValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Securite de l'API: authentification SANS ETAT par jeton JWT (HMAC-SHA256).
 *
 * Les briques du cours "Authentification dans Spring Boot avec Spring Security":
 *   - SecurityFilterChain   : les regles d'acces ci-dessous, appliquees a chaque requete
 *   - AuthenticationManager : verifie login + mot de passe a la connexion (AuthService.login)
 *   - UserDetailsService    : CustomUserDetailsService charge l'utilisateur depuis la base
 *   - PasswordEncoder       : BCryptPasswordEncoder, mots de passe stockes haches
 *
 * Demarche JWT du cours: (1) le client envoie login et mot de passe, (2) l'AuthenticationManager
 * les verifie, (3) TokenService met le nom et le role dans le jeton, (4) le jeton est signe,
 * (5) le client le renvoie a chaque requete dans "Authorization: Bearer <jeton>".
 *
 * Regles d'acces par acteur (cahier des charges, section 5):
 *   - Visiteur (non authentifie): consultation du marche, inscription, connexion
 *   - Trader: ordres, wallet, historique personnel
 *   - Admin: tout ce qui est sous /api/admin (il ne passe pas d'ordres)
 *   - Tout utilisateur connecte: son profil, son mot de passe, sa deconnexion
 * Ces regles sont doublees par @PreAuthorize sur les controleurs (defense en profondeur).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter,
                                                   JsonSecurityErrorHandler errorHandler) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API sans cookie de session: pas de risque CSRF
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Visiteur
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/market/**").permitAll()
                .requestMatchers("/ws/**", "/error").permitAll()
                // Administrateur
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Trader
                .requestMatchers("/api/orders/**", "/api/wallet/**", "/api/history/**").hasRole("TRADER")
                // Tout utilisateur connecte (profil, mot de passe, deconnexion)
                .requestMatchers("/api/auth/**").authenticated()
                // Tout le reste est refuse: une nouvelle route doit etre ouverte explicitement
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler));
        return http.build();
    }

    /**
     * Point d'entree de la logique d'authentification (cours: AuthenticationManager).
     * Le DaoAuthenticationProvider charge l'utilisateur avec le UserDetailsService
     * puis compare le mot de passe au hache BCrypt.
     */
    @Bean
    public AuthenticationManager authenticationManager(CustomUserDetailsService userDetailsService,
                                                       BCryptPasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Hachage a sens unique des mots de passe (cours: "Hachage" vs "Chiffrement"). */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Le role porte par la claim "roles" du jeton devient l'autorite ROLE_TRADER / ROLE_ADMIN. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecretKey jwtSigningKey(ExchangeProperties properties) {
        byte[] secret = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    /**
     * Decodage de chaque jeton recu: signature, expiration (validateurs par defaut)
     * puis revocation (deconnexion, suspension, changement de mot de passe ou de role).
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, TokenRevocationValidator revocationValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), revocationValidator));
        return decoder;
    }

    /** Le front Next.js tourne sur un autre port (localhost:3000). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("http://localhost:*"));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
