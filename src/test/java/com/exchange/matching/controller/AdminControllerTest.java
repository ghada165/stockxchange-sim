package com.exchange.matching.controller;

import com.exchange.matching.dto.ActivityLogResponse;
import com.exchange.matching.dto.AdminOverviewResponse;
import com.exchange.matching.dto.AdminUserResponse;
import com.exchange.matching.dto.MarketHoursRequest;
import com.exchange.matching.dto.MarketStatusResponse;
import com.exchange.matching.exception.ForbiddenOperationException;
import com.exchange.matching.exception.ResourceNotFoundException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.model.Role;
import com.exchange.matching.service.ActivityLogService;
import com.exchange.matching.service.AdminService;
import com.exchange.matching.service.MarketHoursService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;
    @MockBean
    private MarketHoursService marketHoursService;
    @MockBean
    private ActivityLogService activityLogService;

    private static AdminUserResponse user(String username, AccountStatus status) {
        return new AdminUserResponse(username, null, null, Role.TRADER, status, Instant.parse("2026-11-01T10:00:00Z"),
            new BigDecimal("100000"), 2);
    }

    @Test
    void overview_shouldReturn200AndKeyFigures() throws Exception {
        when(adminService.overview()).thenReturn(new AdminOverviewResponse(5, 4, 1, 4, 0, 40, 12,
            new BigDecimal("120"), new BigDecimal("27600.00"), true));

        mockMvc.perform(get("/api/admin/overview").principal(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.users").value(5))
            .andExpect(jsonPath("$.suspendedUsers").value(1))
            .andExpect(jsonPath("$.tradesLast24h").value(12))
            .andExpect(jsonPath("$.marketOpen").value(true));
    }

    @Test
    void users_shouldReturn200AndList() throws Exception {
        when(adminService.users()).thenReturn(List.of(user("alice", AccountStatus.ACTIVE), user("bob", AccountStatus.SUSPENDED)));

        mockMvc.perform(get("/api/admin/users").principal(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[1].username").value("bob"))
            .andExpect(jsonPath("$[1].status").value("SUSPENDED"));
    }

    @Test
    void setUserStatus_shouldReturn200AndCallServiceWithAdminName() throws Exception {
        when(adminService.setUserStatus("admin", "bob", AccountStatus.SUSPENDED)).thenReturn(user("bob", AccountStatus.SUSPENDED));

        mockMvc.perform(patch("/api/admin/users/bob/status").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(adminService).setUserStatus("admin", "bob", AccountStatus.SUSPENDED);
    }

    @Test
    void setUserStatus_onOwnAccount_shouldReturn403() throws Exception {
        when(adminService.setUserStatus("admin", "admin", AccountStatus.SUSPENDED))
            .thenThrow(new ForbiddenOperationException("Un administrateur ne peut pas modifier son propre statut"));

        mockMvc.perform(patch("/api/admin/users/admin/status").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void setUserStatus_whenUserNotFound_shouldReturn404() throws Exception {
        when(adminService.setUserStatus("admin", "ghost", AccountStatus.ACTIVE))
            .thenThrow(new ResourceNotFoundException("Utilisateur inconnu: ghost"));

        mockMvc.perform(patch("/api/admin/users/ghost/status").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void setUserStatus_withInvalidStatus_shouldReturn400() throws Exception {
        mockMvc.perform(patch("/api/admin/users/bob/status").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BANNED\"}"))
            .andExpect(status().isBadRequest());

        verify(adminService, never()).setUserStatus(any(), any(), any());
    }

    @Test
    void updateMarketHours_shouldReturn200AndNewStatus() throws Exception {
        when(marketHoursService.update(any(MarketHoursRequest.class), eq("admin"))).thenReturn(new MarketStatusResponse(
            true, true, LocalTime.of(9, 0), LocalTime.of(17, 30), "Europe/Paris", List.of(DayOfWeek.MONDAY), Instant.now()));

        mockMvc.perform(put("/api/admin/market-hours").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"open\":\"09:00\",\"close\":\"17:30\",\"zone\":\"Europe/Paris\",\"tradingDays\":[\"MONDAY\"],\"enforced\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enforced").value(true))
            .andExpect(jsonPath("$.zone").value("Europe/Paris"));
    }

    @Test
    void updateMarketHours_withoutTradingDays_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/admin/market-hours").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"open\":\"09:00\",\"close\":\"17:30\",\"zone\":\"Europe/Paris\",\"tradingDays\":[],\"enforced\":true}"))
            .andExpect(status().isBadRequest());

        verify(marketHoursService, never()).update(any(), any());
    }

    @Test
    void logs_shouldPassFiltersToService() throws Exception {
        when(activityLogService.recent("alice", ActivityType.ORDER_PLACED)).thenReturn(List.of(
            new ActivityLogResponse(7L, Instant.now(), "alice", ActivityType.ORDER_PLACED, "BUY 1 AAPL")));

        mockMvc.perform(get("/api/admin/logs").principal(ADMIN).param("actor", "alice").param("action", "ORDER_PLACED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].action").value("ORDER_PLACED"));

        verify(activityLogService).recent("alice", ActivityType.ORDER_PLACED);
    }

    @Test
    void logs_withUnknownAction_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/admin/logs").principal(ADMIN).param("action", "NOPE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Valeur invalide pour le parametre action"));
    }
}
