package com.exchange.matching.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation Swagger / OpenAPI de l'API REST (springdoc-openapi).
 *
 *   Interface Swagger UI : http://localhost:8080/swagger-ui.html
 *   Description JSON     : http://localhost:8080/v3/api-docs
 *
 * Pour tester un endpoint protege dans Swagger UI: appeler POST /api/auth/login,
 * copier le champ "token" de la reponse, cliquer sur "Authorize" et le coller.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
    title = "StockXchange Sim API",
    version = "1.0",
    description = "Bourse simulee: moteur de matching d'ordres (priorite prix puis temps), marche, ordres, "
        + "wallet, historique et administration. Authentification par jeton JWT (Bearer)."
))
@SecurityScheme(
    name = OpenApiConfig.BEARER,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Jeton renvoye par POST /api/auth/login ou /api/auth/register"
)
public class OpenApiConfig {

    /** Nom du schema de securite reference par @SecurityRequirement sur les controllers. */
    public static final String BEARER = "bearerAuth";
}
