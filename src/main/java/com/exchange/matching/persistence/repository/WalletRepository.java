package com.exchange.matching.persistence.repository;

import com.exchange.matching.persistence.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<WalletEntity, String> {

    Optional<WalletEntity> findByUserId(String userId);

    /**
     * Verrou pessimiste (SELECT ... FOR UPDATE) pour les operations qui
     * modifient le solde. Complementaire au @Version (verrou optimiste):
     * ici on veut serialiser explicitement les acces concurrents au meme
     * wallet pendant le reglement d'un trade, plutot que de laisser
     * echouer une transaction sur 2 avec une OptimisticLockException.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletEntity w where w.userId = :userId")
    Optional<WalletEntity> findByUserIdForUpdate(String userId);
}
