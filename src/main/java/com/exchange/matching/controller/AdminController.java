package com.exchange.matching.controller;

import com.exchange.matching.dto.ActivityLogResponse;
import com.exchange.matching.dto.AdminOverviewResponse;
import com.exchange.matching.dto.AdminUserResponse;
import com.exchange.matching.dto.CreateSymbolRequest;
import com.exchange.matching.dto.MarketHoursRequest;
import com.exchange.matching.dto.MarketStatusResponse;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.RoleUpdateRequest;
import com.exchange.matching.dto.StatusUpdateRequest;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.service.ActivityLogService;
import com.exchange.matching.service.AdminService;
import com.exchange.matching.service.MarketHoursService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Module Administration (role ADMIN uniquement, voir SecurityConfig). */
@RestController
@PreAuthorize("hasRole('ADMIN')") // double la regle d'URL de SecurityConfig (defense en profondeur)
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final MarketHoursService marketHours;
    private final ActivityLogService activityLog;

    public AdminController(AdminService adminService, MarketHoursService marketHours, ActivityLogService activityLog) {
        this.adminService = adminService;
        this.marketHours = marketHours;
        this.activityLog = activityLog;
    }

    /** GET /api/admin/overview : chiffres cles de la plateforme. */
    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return adminService.overview();
    }

    /** GET /api/admin/users : supervision des utilisateurs. */
    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminService.users();
    }

    /** PATCH /api/admin/users/{username}/status : activer ou suspendre un utilisateur. */
    @PatchMapping("/users/{username}/status")
    public AdminUserResponse setUserStatus(Authentication authentication, @PathVariable String username,
                                           @Valid @RequestBody StatusUpdateRequest request) {
        return adminService.setUserStatus(authentication.getName(), username, request.status());
    }

    /** PATCH /api/admin/users/{username}/role : attribuer le role TRADER ou ADMIN. */
    @PatchMapping("/users/{username}/role")
    public AdminUserResponse setUserRole(Authentication authentication, @PathVariable String username,
                                         @Valid @RequestBody RoleUpdateRequest request) {
        return adminService.setUserRole(authentication.getName(), username, request.role());
    }

    /** POST /api/admin/users/{username}/unlock : lever un verrouillage apres echecs de connexion. */
    @PostMapping("/users/{username}/unlock")
    public AdminUserResponse unlockUser(Authentication authentication, @PathVariable String username) {
        return adminService.unlockUser(authentication.getName(), username);
    }

    /** POST /api/admin/symbols : ajouter un symbole (introduction en bourse). */
    @PostMapping("/symbols")
    public ResponseEntity<MarketSummaryResponse> createSymbol(Authentication authentication,
                                                              @Valid @RequestBody CreateSymbolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createSymbol(authentication.getName(), request));
    }

    /** PATCH /api/admin/symbols/{symbol}/status : suspendre ou reactiver la cotation d'un symbole. */
    @PatchMapping("/symbols/{symbol}/status")
    public MarketSummaryResponse setSymbolStatus(Authentication authentication, @PathVariable String symbol,
                                                 @Valid @RequestBody StatusUpdateRequest request) {
        return adminService.setSymbolStatus(authentication.getName(), symbol, request.status());
    }

    /** PUT /api/admin/market-hours : definir les heures d'ouverture du marche. */
    @PutMapping("/market-hours")
    public MarketStatusResponse updateMarketHours(Authentication authentication,
                                                  @Valid @RequestBody MarketHoursRequest request) {
        return marketHours.update(request, authentication.getName());
    }

    /** GET /api/admin/logs?actor=alice&action=ORDER_PLACED : journal d'activite global (200 dernieres lignes). */
    @GetMapping("/logs")
    public List<ActivityLogResponse> logs(@RequestParam(required = false) String actor,
                                          @RequestParam(required = false) ActivityType action) {
        return activityLog.recent(actor, action);
    }
}
