package com.exchange.matching.persistence;

import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Regles de securite portees par le compte: verrouillage et invalidation des jetons. */
class UserEntityTest {

    private static final Duration LOCK = Duration.ofMinutes(15);

    private UserEntity user() {
        return new UserEntity("alice", "{bcrypt}hash", "Alice", null, Role.TRADER);
    }

    @Test
    void verrouille_au_cinquieme_echec_consecutif() {
        UserEntity user = user();
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        for (int i = 1; i <= 4; i++) {
            assertThat(user.registerFailedLogin(5, LOCK, now)).isFalse();
        }
        assertThat(user.isLocked(now)).isFalse();

        assertThat(user.registerFailedLogin(5, LOCK, now)).isTrue();
        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.isLocked(now.plus(LOCK).minusSeconds(1))).isTrue();
        assertThat(user.isLocked(now.plus(LOCK))).isFalse(); // deverrouillage automatique
    }

    @Test
    void une_connexion_reussie_remet_le_compteur_a_zero() {
        UserEntity user = user();
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            user.registerFailedLogin(5, LOCK, now);
        }
        user.registerSuccessfulLogin();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.registerFailedLogin(5, LOCK, now)).isFalse(); // le compte repart de zero
    }

    @Test
    void changer_le_mot_de_passe_invalide_les_jetons_existants() {
        UserEntity user = user();
        int before = user.getTokenVersion();
        user.changePassword("{bcrypt}nouveau");
        assertThat(user.getTokenVersion()).isEqualTo(before + 1);
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}nouveau");
    }
}
