package com.exchange.matching.controller;

import com.exchange.matching.dto.AuthResponse;
import com.exchange.matching.dto.LoginRequest;
import com.exchange.matching.dto.RegisterRequest;
import com.exchange.matching.dto.UpdateProfileRequest;
import com.exchange.matching.dto.UserProfileResponse;
import com.exchange.matching.exception.AuthenticationFailedException;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.Role;
import com.exchange.matching.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final Authentication ALICE = new UsernamePasswordAuthenticationToken("alice", null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    private static UserProfileResponse profile(String username, String displayName, String email) {
        return new UserProfileResponse(username, displayName, email, Role.TRADER, AccountStatus.ACTIVE,
            Instant.parse("2026-11-01T10:00:00Z"));
    }

    private static AuthResponse auth(String username) {
        return new AuthResponse("jwt-token", Instant.parse("2026-11-16T20:00:00Z"), profile(username, null, null));
    }

    @Test
    void register_shouldReturn201WithToken() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(auth("dave"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dave\",\"password\":\"motdepasse1\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value("jwt-token"))
            .andExpect(jsonPath("$.user.username").value("dave"))
            .andExpect(jsonPath("$.user.role").value("TRADER"));

        verify(authService).register(new RegisterRequest("dave", "motdepasse1", null, null));
    }

    @Test
    void register_withShortPassword_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dave\",\"password\":\"123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("password: password doit faire entre 8 et 72 caracteres"));

        verify(authService, never()).register(any());
    }

    @Test
    void register_withTakenUsername_shouldReturn409() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
            .thenThrow(new IllegalStateException("Le nom d'utilisateur 'alice' est deja pris"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"motdepasse1\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void login_shouldReturn200WithToken() throws Exception {
        when(authService.login(new LoginRequest("alice", "password123"))).thenReturn(auth("alice"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt-token"))
            .andExpect(jsonPath("$.expiresAt").value("2026-11-16T20:00:00Z"));
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
            .thenThrow(new AuthenticationFailedException("Nom d'utilisateur ou mot de passe incorrect"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Nom d'utilisateur ou mot de passe incorrect"));
    }

    @Test
    void login_withSuspendedAccount_shouldReturn403() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new ForbiddenOperationException("Ce compte est suspendu"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"eve\",\"password\":\"motdepasse1\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void login_withEmptyBody_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }

    @Test
    void me_shouldReturnProfileOfConnectedUser() throws Exception {
        when(authService.profile("alice")).thenReturn(profile("alice", "Alice", "alice@mail.com"));

        mockMvc.perform(get("/api/auth/me").principal(ALICE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@mail.com"));
    }

    @Test
    void updateProfile_shouldReturn200AndUpdatedProfile() throws Exception {
        when(authService.updateProfile("alice", new UpdateProfileRequest("Alice B.", "alice.b@mail.com")))
            .thenReturn(profile("alice", "Alice B.", "alice.b@mail.com"));

        mockMvc.perform(put("/api/auth/me").principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Alice B.\",\"email\":\"alice.b@mail.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName").value("Alice B."))
            .andExpect(jsonPath("$.email").value("alice.b@mail.com"));
    }

    @Test
    void updateProfile_withInvalidEmail_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/auth/me").principal(ALICE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Alice\",\"email\":\"pas-un-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("email: email invalide"));

        verify(authService, never()).updateProfile(any(), any());
    }
}
