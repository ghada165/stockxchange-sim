package com.exchange.matching.controller;

import com.exchange.matching.config.OpenApiConfig;
import com.exchange.matching.dto.ActivityLogResponse;
import com.exchange.matching.dto.AdminOverviewResponse;
import com.exchange.matching.dto.AdminUserResponse;
import com.exchange.matching.dto.MarketHoursRequest;
import com.exchange.matching.dto.MarketStatusResponse;
import com.exchange.matching.dto.RoleUpdateRequest;
import com.exchange.matching.dto.StatusUpdateRequest;
import com.exchange.matching.model.ActivityType;
import com.exchange.matching.service.ActivityLogService;
import com.exchange.matching.service.AdminService;
import com.exchange.matching.service.MarketHoursService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module Administration (role ADMIN uniquement, voir SecurityConfig):
 * tableau de bord, utilisateurs, heures de marche, journal d'activite.
 * Le CRUD des symboles est dans {@link SymbolAdminController}.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')") // double la regle d'URL de SecurityConfig (defense en profondeur)
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Tableau de bord, utilisateurs, heures de marche, journal (role ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminController {

    @Autowired
    private AdminService adminService;
    @Autowired
    private MarketHoursService marketHours;
    @Autowired
    private ActivityLogService activityLog;

    @GetMapping("/overview")
    @Operation(summary = "Tableau de bord", description = "Utilisateurs, symboles, ordres actifs, volume 24 h, etat du marche.")
    public AdminOverviewResponse overview() {
        return adminService.overview();
    }

    @GetMapping("/users")
    @Operation(summary = "Lister les utilisateurs", description = "Avec solde et nombre d'ordres actifs.")
    public List<AdminUserResponse> users() {
        return adminService.users();
    }

    @PatchMapping("/users/{username}/status")
    @Operation(summary = "Suspendre ou reactiver un utilisateur")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statut modifie"),
        @ApiResponse(responseCode = "403", description = "Un admin ne peut pas modifier son propre statut"),
        @ApiResponse(responseCode = "404", description = "Utilisateur inconnu")
    })
    public AdminUserResponse setUserStatus(Authentication authentication,
                                           @Parameter(description = "Nom d'utilisateur", example = "alice")
                                           @PathVariable String username,
                                           @Valid @RequestBody StatusUpdateRequest request) {
        return adminService.setUserStatus(authentication.getName(), username, request.status());
    }

    @PatchMapping("/users/{username}/role")
    @Operation(summary = "Attribuer un role", description = "TRADER ou ADMIN. Les jetons existants de l'utilisateur sont invalides: il se reconnecte avec ses nouveaux droits.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Role modifie"),
        @ApiResponse(responseCode = "403", description = "Son propre role, ou dernier administrateur actif"),
        @ApiResponse(responseCode = "404", description = "Utilisateur inconnu")
    })
    public AdminUserResponse setUserRole(Authentication authentication,
                                         @Parameter(description = "Nom d'utilisateur", example = "alice")
                                         @PathVariable String username,
                                         @Valid @RequestBody RoleUpdateRequest request) {
        return adminService.setUserRole(authentication.getName(), username, request.role());
    }

    @PostMapping("/users/{username}/unlock")
    @Operation(summary = "Deverrouiller un compte", description = "Leve le verrouillage pose apres trop d'echecs de connexion.")
    public AdminUserResponse unlockUser(Authentication authentication,
                                        @Parameter(description = "Nom d'utilisateur", example = "alice")
                                        @PathVariable String username) {
        return adminService.unlockUser(authentication.getName(), username);
    }

    @PutMapping("/market-hours")
    @Operation(summary = "Definir les heures d'ouverture du marche")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Horaires enregistres"),
        @ApiResponse(responseCode = "400", description = "Horaires ou fuseau invalides")
    })
    public MarketStatusResponse updateMarketHours(Authentication authentication,
                                                  @Valid @RequestBody MarketHoursRequest request) {
        return marketHours.update(request, authentication.getName());
    }

    @GetMapping("/logs")
    @Operation(summary = "Journal d'activite global", description = "200 dernieres lignes, filtrables par acteur ou par action.")
    public List<ActivityLogResponse> logs(@Parameter(description = "Filtrer par utilisateur", example = "alice")
                                          @RequestParam(required = false) String actor,
                                          @Parameter(description = "Filtrer par type d'action")
                                          @RequestParam(required = false) ActivityType action) {
        return activityLog.recent(actor, action);
    }
}
