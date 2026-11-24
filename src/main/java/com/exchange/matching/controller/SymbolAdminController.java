package com.exchange.matching.controller;

import com.exchange.matching.config.OpenApiConfig;
import com.exchange.matching.dto.CreateSymbolRequest;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.StatusUpdateRequest;
import com.exchange.matching.dto.UpdateSymbolRequest;
import com.exchange.matching.service.SymbolAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD des symboles cotes (role ADMIN uniquement, voir SecurityConfig). */
@RestController
@RequestMapping("/api/admin/symbols")
@Tag(name = "Admin - Symboles", description = "CRUD des actions cotees (role ADMIN)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class SymbolAdminController {

    @Autowired
    private SymbolAdminService symbolAdminService;

    @PostMapping
    @Operation(summary = "Ajouter un symbole", description = "Introduction en bourse: cotation, dotation d'actions "
        + "pour chaque compte et amorcage du carnet par le teneur de marche.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Symbole cree"),
        @ApiResponse(responseCode = "400", description = "Requete invalide"),
        @ApiResponse(responseCode = "409", description = "Le symbole existe deja")
    })
    public ResponseEntity<MarketSummaryResponse> create(Authentication authentication,
                                                        @Valid @RequestBody CreateSymbolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(symbolAdminService.create(authentication.getName(), request));
    }

    @GetMapping
    @Operation(summary = "Lister les symboles", description = "Statut, prix de reference, dernier prix et meilleurs prix.")
    public List<MarketSummaryResponse> findAll() {
        return symbolAdminService.findAll();
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Detail d'un symbole")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Symbole trouve"),
        @ApiResponse(responseCode = "404", description = "Symbole inconnu")
    })
    public MarketSummaryResponse findById(@Parameter(description = "Code du symbole", example = "AAPL")
                                          @PathVariable String symbol) {
        return symbolAdminService.findById(symbol);
    }

    @PutMapping("/{symbol}")
    @Operation(summary = "Modifier un symbole", description = "Change le nom et le prix de reference.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Symbole modifie"),
        @ApiResponse(responseCode = "400", description = "Requete invalide"),
        @ApiResponse(responseCode = "404", description = "Symbole inconnu")
    })
    public MarketSummaryResponse update(Authentication authentication,
                                        @Parameter(description = "Code du symbole", example = "AAPL")
                                        @PathVariable String symbol,
                                        @Valid @RequestBody UpdateSymbolRequest request) {
        return symbolAdminService.update(authentication.getName(), symbol, request);
    }

    @PatchMapping("/{symbol}/status")
    @Operation(summary = "Suspendre ou reactiver la cotation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Statut modifie"),
        @ApiResponse(responseCode = "404", description = "Symbole inconnu")
    })
    public MarketSummaryResponse setStatus(Authentication authentication,
                                           @Parameter(description = "Code du symbole", example = "AAPL")
                                           @PathVariable String symbol,
                                           @Valid @RequestBody StatusUpdateRequest request) {
        return symbolAdminService.setStatus(authentication.getName(), symbol, request.status());
    }

    @DeleteMapping("/{symbol}")
    @Operation(summary = "Supprimer un symbole", description = "Possible seulement si aucun utilisateur n'a encore "
        + "passe d'ordre dessus; sinon, suspendre la cotation.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Symbole supprime"),
        @ApiResponse(responseCode = "404", description = "Symbole inconnu"),
        @ApiResponse(responseCode = "409", description = "Symbole deja echange: suppression refusee")
    })
    public ResponseEntity<Void> delete(Authentication authentication,
                                       @Parameter(description = "Code du symbole", example = "AAPL")
                                       @PathVariable String symbol) {
        symbolAdminService.delete(authentication.getName(), symbol);
        return ResponseEntity.noContent().build();
    }
}
