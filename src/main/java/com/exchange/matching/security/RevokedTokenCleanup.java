package com.exchange.matching.security;

import com.exchange.matching.persistence.repository.RevokedTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Purge horaire des jetons revoques deja expires: la liste de revocation reste petite. */
@Component
public class RevokedTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanup.class);

    private final RevokedTokenRepository revokedTokens;

    public RevokedTokenCleanup(RevokedTokenRepository revokedTokens) {
        this.revokedTokens = revokedTokens;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void purgeExpired() {
        int deleted = revokedTokens.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("{} jeton(s) revoque(s) expire(s) supprime(s)", deleted);
        }
    }
}
