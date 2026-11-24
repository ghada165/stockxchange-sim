package com.exchange.matching.controller;

import com.exchange.matching.config.OpenApiConfig;
import com.exchange.matching.dto.TradeHistoryResponse;
import com.exchange.matching.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Module Historique & Reporting, partie personnelle (role TRADER). */
@RestController
@PreAuthorize("hasRole('TRADER')") // double la regle d'URL de SecurityConfig (defense en profondeur)
@RequestMapping("/api/history")
@Tag(name = "Historique", description = "Releve personnel des executions (role TRADER)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class HistoryController {

    @Autowired
    private ReportingService reportingService;

    /** GET /api/history/trades : releve des executions de l'utilisateur, avec synthese. */
    @GetMapping("/trades")
    @Operation(summary = "Mon releve", description = "500 dernieres executions et synthese (achats, ventes, flux net).")
    public TradeHistoryResponse myTrades(Authentication authentication) {
        return reportingService.history(authentication.getName());
    }
}
