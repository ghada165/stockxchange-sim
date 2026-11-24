package com.exchange.matching.security;

import com.exchange.matching.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Reponses d'erreur de la chaine de filtres de securite, au meme format JSON
 * (record ApiError) que le reste de l'API:
 *   401 Unauthorized: pas de jeton, jeton invalide, expire ou revoque
 *   403 Forbidden:    jeton valide mais role insuffisant
 * L'en-tete WWW-Authenticate reste present, comme l'exige la norme Bearer (RFC 6750).
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        boolean badToken = exception instanceof InvalidBearerTokenException;
        String message = badToken
            ? "Jeton invalide, expire ou revoque: reconnectez-vous"
            : "Authentification requise: envoyez l'en-tete Authorization: Bearer <jeton>";
        response.setHeader("WWW-Authenticate", badToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, message);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Acces refuse: votre role ne permet pas cette operation");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
            new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }
}
