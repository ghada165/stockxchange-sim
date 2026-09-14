package com.exchange.matching.config;

import com.exchange.matching.engine.MatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le MatchingEngine est une classe Java pure (aucune dependance Spring),
 * testee en isolation totale (voir MatchingEngineTest / MatchingEngineConcurrencyTest).
 * On l'expose ici comme bean singleton pour que Spring l'injecte dans les
 * services qui en ont besoin, sans coupler le moteur lui-meme au framework.
 */
@Configuration
public class EngineConfig {

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
