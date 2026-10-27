package com.exchange.matching.controller;

import com.exchange.matching.dto.TradeHistoryResponse;
import com.exchange.matching.service.ReportingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Module Historique & Reporting, partie personnelle (role TRADER). */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final ReportingService reportingService;

    public HistoryController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /** GET /api/history/trades : releve des executions de l'utilisateur, avec synthese. */
    @GetMapping("/trades")
    public TradeHistoryResponse myTrades(Authentication authentication) {
        return reportingService.history(authentication.getName());
    }
}
