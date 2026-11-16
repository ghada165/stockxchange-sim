package com.exchange.matching.controller;

import com.exchange.matching.dto.CreateSymbolRequest;
import com.exchange.matching.dto.MarketSummaryResponse;
import com.exchange.matching.dto.UpdateSymbolRequest;
import com.exchange.matching.exception.ResourceNotFoundException;
import com.exchange.matching.model.AccountStatus;
import com.exchange.matching.service.SymbolAdminService;
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
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests unitaires du controller CRUD des symboles (TP 5, tache 1).
 * Seule la couche web est chargee; le service est remplace par un mock.
 * Les filtres de securite sont desactives: l'utilisateur connecte est
 * fourni directement par .principal(...). Les droits sont testes dans ApiIntegrationTest.
 */
@WebMvcTest(SymbolAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class SymbolAdminControllerTest {

    private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SymbolAdminService symbolAdminService;

    private static MarketSummaryResponse summary(String symbol, String name, String price, AccountStatus status) {
        return new MarketSummaryResponse(symbol, name, status, new BigDecimal(price), null,
            new BigDecimal("229.54"), new BigDecimal("230.46"));
    }

    // ---------- CREATE ----------

    @Test
    void create_shouldReturn201AndCreatedSymbol() throws Exception {
        when(symbolAdminService.create(eq("admin"), any(CreateSymbolRequest.class)))
            .thenReturn(summary("NVDA", "Nvidia Corp.", "140.00", AccountStatus.ACTIVE));

        mockMvc.perform(post("/api/admin/symbols").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"NVDA\",\"name\":\"Nvidia Corp.\",\"referencePrice\":140.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.symbol").value("NVDA"))
            .andExpect(jsonPath("$.name").value("Nvidia Corp."))
            .andExpect(jsonPath("$.referencePrice").value(140.00))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(symbolAdminService, times(1)).create(eq("admin"), any(CreateSymbolRequest.class));
    }

    @Test
    void create_withInvalidBody_shouldReturn400WithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/admin/symbols").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"nvda\",\"name\":\"\",\"referencePrice\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verify(symbolAdminService, never()).create(any(), any());
    }

    @Test
    void create_whenSymbolAlreadyExists_shouldReturn409() throws Exception {
        when(symbolAdminService.create(eq("admin"), any(CreateSymbolRequest.class)))
            .thenThrow(new IllegalStateException("Le symbole AAPL existe deja"));

        mockMvc.perform(post("/api/admin/symbols").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"name\":\"Apple\",\"referencePrice\":230.00}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Le symbole AAPL existe deja"));
    }

    // ---------- READ ALL ----------

    @Test
    void findAll_shouldReturn200AndAllSymbols() throws Exception {
        when(symbolAdminService.findAll()).thenReturn(List.of(
            summary("AAPL", "Apple Inc.", "230.00", AccountStatus.ACTIVE),
            summary("TSLA", "Tesla Inc.", "250.00", AccountStatus.SUSPENDED)));

        mockMvc.perform(get("/api/admin/symbols").principal(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].symbol").value("AAPL"))
            .andExpect(jsonPath("$[1].symbol").value("TSLA"))
            .andExpect(jsonPath("$[1].status").value("SUSPENDED"));

        verify(symbolAdminService).findAll();
    }

    @Test
    void findAll_whenNoSymbols_shouldReturnEmptyArray() throws Exception {
        when(symbolAdminService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/symbols").principal(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- READ BY ID ----------

    @Test
    void findById_whenExists_shouldReturn200AndSymbol() throws Exception {
        when(symbolAdminService.findById("AAPL")).thenReturn(summary("AAPL", "Apple Inc.", "230.00", AccountStatus.ACTIVE));

        mockMvc.perform(get("/api/admin/symbols/AAPL").principal(ADMIN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("AAPL"))
            .andExpect(jsonPath("$.bestBid").value(229.54))
            .andExpect(jsonPath("$.bestAsk").value(230.46));

        verify(symbolAdminService).findById("AAPL");
    }

    @Test
    void findById_whenNotFound_shouldReturn404() throws Exception {
        when(symbolAdminService.findById("XXX")).thenThrow(new ResourceNotFoundException("Symbole inconnu: XXX"));

        mockMvc.perform(get("/api/admin/symbols/XXX").principal(ADMIN))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Symbole inconnu: XXX"));
    }

    // ---------- UPDATE ----------

    @Test
    void update_whenExists_shouldReturn200AndUpdatedSymbol() throws Exception {
        when(symbolAdminService.update(eq("admin"), eq("AAPL"), any(UpdateSymbolRequest.class)))
            .thenReturn(summary("AAPL", "Apple Computer", "235.50", AccountStatus.ACTIVE));

        mockMvc.perform(put("/api/admin/symbols/AAPL").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Apple Computer\",\"referencePrice\":235.50}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Apple Computer"))
            .andExpect(jsonPath("$.referencePrice").value(235.50));

        verify(symbolAdminService).update("admin", "AAPL",
            new UpdateSymbolRequest("Apple Computer", new BigDecimal("235.50")));
    }

    @Test
    void update_whenNotFound_shouldReturn404() throws Exception {
        when(symbolAdminService.update(eq("admin"), eq("XXX"), any(UpdateSymbolRequest.class)))
            .thenThrow(new ResourceNotFoundException("Symbole inconnu: XXX"));

        mockMvc.perform(put("/api/admin/symbols/XXX").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"referencePrice\":10.00}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_withMissingPrice_shouldReturn400() throws Exception {
        mockMvc.perform(put("/api/admin/symbols/AAPL").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Apple\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("referencePrice: referencePrice est requis"));

        verify(symbolAdminService, never()).update(any(), any(), any());
    }

    @Test
    void setStatus_shouldReturn200AndNewStatus() throws Exception {
        when(symbolAdminService.setStatus("admin", "TSLA", AccountStatus.SUSPENDED))
            .thenReturn(summary("TSLA", "Tesla Inc.", "250.00", AccountStatus.SUSPENDED));

        mockMvc.perform(patch("/api/admin/symbols/TSLA/status").principal(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(symbolAdminService).setStatus("admin", "TSLA", AccountStatus.SUSPENDED);
    }

    // ---------- DELETE ----------

    @Test
    void delete_shouldReturn204AndCallService() throws Exception {
        doNothing().when(symbolAdminService).delete("admin", "NVDA");

        mockMvc.perform(delete("/api/admin/symbols/NVDA").principal(ADMIN))
            .andExpect(status().isNoContent());

        verify(symbolAdminService, times(1)).delete("admin", "NVDA");
    }

    @Test
    void delete_whenNotFound_shouldReturn404() throws Exception {
        doThrow(new ResourceNotFoundException("Symbole inconnu: XXX")).when(symbolAdminService).delete("admin", "XXX");

        mockMvc.perform(delete("/api/admin/symbols/XXX").principal(ADMIN))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_whenAlreadyTraded_shouldReturn409() throws Exception {
        doThrow(new IllegalStateException("Impossible de supprimer AAPL")).when(symbolAdminService).delete("admin", "AAPL");

        mockMvc.perform(delete("/api/admin/symbols/AAPL").principal(ADMIN))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Impossible de supprimer AAPL"));
    }
}
