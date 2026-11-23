package com.exchange.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de bout en bout de l'API: demarre toute l'application (H2 en
 * memoire, Flyway, securite JWT, teneur de marche) et joue les workflows
 * du cahier des charges via des requetes HTTP simulees (MockMvc).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void visiteur_consulte_le_marche_mais_ne_peut_pas_trader() throws Exception {
        mvc.perform(get("/api/market/symbols"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("AAPL"));
        mvc.perform(get("/api/market/AAPL/orderbook"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.asks[0].price").exists());
        mvc.perform(get("/api/market/status")).andExpect(status().isOk());

        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void inscription_puis_ordre_au_marche_puis_historique_et_statistiques() throws Exception {
        String token = register("dave", "motdepasse1");

        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableBalance").value(100000.0))
            .andExpect(jsonPath("$.positions.length()").value(greaterThanOrEqualTo(4))); // 5 si NVDA a deja ete cree

        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"MSFT\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.order.status").value("FILLED"));

        mvc.perform(get("/api/history/trades").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.buyCount").value(2)) // 4 a 420.84 puis 1 a 421.68
            .andExpect(jsonPath("$.trades[0].side").value("BUY"));

        mvc.perform(get("/api/market/MSFT/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tradeCount").value(2))
            .andExpect(jsonPath("$.volume").value(5.0));

        mvc.perform(get("/api/market/MSFT/candles").param("interval", "1m"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].volume").value(5.0));
    }

    @Test
    void un_trader_n_a_pas_acces_a_l_administration() throws Exception {
        String token = login("alice", "password123");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void mauvais_mot_de_passe_refuse() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"faux\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_suspend_un_trader_qui_ne_peut_plus_se_connecter() throws Exception {
        register("eve", "motdepasse1");
        String admin = login("admin", "admin123");

        mvc.perform(patch("/api/admin/users/eve/status").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"eve\",\"password\":\"motdepasse1\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/logs").param("actor", "eve").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action").value("LOGIN_FAILED"));
    }

    @Test
    void admin_cree_un_symbole_qui_devient_negociable() throws Exception {
        String admin = login("admin", "admin123");
        mvc.perform(post("/api/admin/symbols").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"NVDA\",\"name\":\"Nvidia Corp.\",\"referencePrice\":140.00}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.bestAsk").exists());

        String trader = login("carol", "password123");
        mvc.perform(post("/api/orders").header("Authorization", "Bearer " + trader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"NVDA\",\"side\":\"SELL\",\"type\":\"LIMIT\",\"price\":150.00,\"quantity\":10}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.order.status").value("OPEN"));
    }

    @Test
    void marche_ferme_refuse_les_ordres() throws Exception {
        String admin = login("admin", "admin123");
        DayOfWeek today = ZonedDateTime.now(ZoneId.of("Europe/Paris")).getDayOfWeek();
        String otherDays = Arrays.stream(DayOfWeek.values())
            .filter(d -> d != today)
            .map(d -> "\"" + d + "\"")
            .collect(Collectors.joining(","));

        mvc.perform(put("/api/admin/market-hours").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"open\":\"00:00\",\"close\":\"23:59\",\"zone\":\"Europe/Paris\",\"tradingDays\":[" + otherDays + "],\"enforced\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(false));
        try {
            String trader = login("bob", "password123");
            mvc.perform(post("/api/orders").header("Authorization", "Bearer " + trader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"type\":\"LIMIT\",\"price\":200.00,\"quantity\":1}"))
                .andExpect(status().isConflict());
        } finally {
            mvc.perform(put("/api/admin/market-hours").header("Authorization", "Bearer " + admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"open\":\"09:00\",\"close\":\"17:30\",\"zone\":\"Europe/Paris\",\"tradingDays\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"],\"enforced\":false}"))
                .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------
    // Authentification et regles d'acces
    // ------------------------------------------------------------------

    @Test
    void sans_jeton_401_en_json_et_route_inconnue_refusee() throws Exception {
        mvc.perform(get("/api/wallet"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer jeton.faux.invalide"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/route-inconnue")).andExpect(status().isUnauthorized());

        String token = login("alice", "password123");
        mvc.perform(get("/api/route-inconnue").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void mot_de_passe_trop_faible_refuse_a_l_inscription() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"faible\",\"password\":\"motdepasse\"}")) // pas de chiffre
            .andExpect(status().isBadRequest());
    }

    @Test
    void la_deconnexion_revoque_le_jeton() throws Exception {
        String token = register("frank", "motdepasse1");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("frank"));

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void changer_de_mot_de_passe_invalide_les_anciens_jetons() throws Exception {
        String oldToken = register("grace", "motdepasse1");

        mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"mauvais1\",\"newPassword\":\"nouveau2026\"}"))
            .andExpect(status().isUnauthorized());

        String body = mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + oldToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"motdepasse1\",\"newPassword\":\"nouveau2026\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String newToken = token(body);

        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + oldToken))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + newToken))
            .andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"grace\",\"password\":\"motdepasse1\"}"))
            .andExpect(status().isUnauthorized());
        login("grace", "nouveau2026");
    }

    @Test
    void compte_verrouille_apres_cinq_echecs_puis_deverrouille_par_l_admin() throws Exception {
        register("henri", "motdepasse1");
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"henri\",\"password\":\"mauvais" + i + "\"}"))
                .andExpect(status().isUnauthorized());
        }
        // Meme le bon mot de passe est refuse pendant le verrouillage
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"henri\",\"password\":\"motdepasse1\"}"))
            .andExpect(status().isLocked());

        String admin = login("admin", "admin123");
        mvc.perform(post("/api/admin/users/henri/unlock").header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk());
        login("henri", "motdepasse1");
    }

    @Test
    void la_suspension_coupe_l_acces_immediatement() throws Exception {
        String token = register("ivan", "motdepasse1");
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        String admin = login("admin", "admin123");
        mvc.perform(patch("/api/admin/users/ivan/status").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void attribution_du_role_admin_par_un_administrateur() throws Exception {
        String traderToken = register("julia", "motdepasse1");
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + traderToken))
            .andExpect(status().isForbidden());

        String admin = login("admin", "admin123");
        mvc.perform(patch("/api/admin/users/julia/role").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));

        // L'ancien jeton (role TRADER) est invalide; le nouveau porte le role ADMIN
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + traderToken))
            .andExpect(status().isUnauthorized());
        String adminToken = login("julia", "motdepasse1");
        mvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
        mvc.perform(get("/api/wallet").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden()); // un admin ne trade pas

        // Un administrateur ne peut pas modifier son propre role
        mvc.perform(patch("/api/admin/users/admin/role").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"TRADER\"}"))
            .andExpect(status().isForbidden());
    }

    // --- Helpers ---

    private String register(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return token(body);
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return token(body);
    }

    private String token(String body) throws Exception {
        JsonNode node = json.readTree(body);
        String token = node.get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
