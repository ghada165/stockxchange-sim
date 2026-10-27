package com.exchange.matching.service;

import com.exchange.matching.exception.MarketClosedException;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.repository.SymbolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Referentiel des symboles cotes (module Marche). */
@Service
public class SymbolService {

    private final SymbolRepository repository;

    public SymbolService(SymbolRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SymbolEntity> list() {
        return repository.findAllByOrderByCreatedAtAscSymbolAsc();
    }

    @Transactional(readOnly = true)
    public List<String> codes() {
        return list().stream().map(SymbolEntity::getSymbol).toList();
    }

    /** @throws IllegalArgumentException si le symbole n'est pas cote */
    @Transactional(readOnly = true)
    public SymbolEntity require(String symbol) {
        return repository.findById(symbol)
            .orElseThrow(() -> new IllegalArgumentException("Symbole inconnu: " + symbol));
    }

    /** @throws MarketClosedException si la cotation du symbole est suspendue */
    @Transactional(readOnly = true)
    public SymbolEntity requireTradable(String symbol) {
        SymbolEntity entity = require(symbol);
        if (!entity.isTradable()) {
            throw new MarketClosedException("La cotation de " + symbol + " est suspendue");
        }
        return entity;
    }
}
