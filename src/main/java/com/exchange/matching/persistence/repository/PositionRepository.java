package com.exchange.matching.persistence.repository;

import com.exchange.matching.persistence.entity.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<PositionEntity, String> {

    List<PositionEntity> findByUserIdOrderBySymbolAsc(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PositionEntity p where p.userId = :userId and p.symbol = :symbol")
    Optional<PositionEntity> findByUserIdAndSymbolForUpdate(String userId, String symbol);
}
