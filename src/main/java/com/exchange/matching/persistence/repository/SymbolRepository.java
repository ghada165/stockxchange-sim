package com.exchange.matching.persistence.repository;

import com.exchange.matching.persistence.entity.SymbolEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SymbolRepository extends JpaRepository<SymbolEntity, String> {

    List<SymbolEntity> findAllByOrderByCreatedAtAscSymbolAsc();
}
