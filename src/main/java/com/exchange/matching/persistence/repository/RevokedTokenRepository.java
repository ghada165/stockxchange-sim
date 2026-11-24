package com.exchange.matching.persistence.repository;

import com.exchange.matching.persistence.entity.RevokedTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedTokenEntity, String> {

    /** Purge des jetons revoques deja expires (ils seraient refuses de toute facon). */
    @Modifying
    @Transactional
    @Query("delete from RevokedTokenEntity t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
