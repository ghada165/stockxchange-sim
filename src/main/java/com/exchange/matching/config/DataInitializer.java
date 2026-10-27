package com.exchange.matching.config;

import com.exchange.matching.model.Role;
import com.exchange.matching.persistence.entity.SymbolEntity;
import com.exchange.matching.persistence.entity.UserEntity;
import com.exchange.matching.persistence.repository.SymbolRepository;
import com.exchange.matching.persistence.repository.UserRepository;
import com.exchange.matching.service.MarketHoursService;
import com.exchange.matching.service.TradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Donnees de depart, creees une seule fois (base vide):
 *   - les symboles cotes de application.yml,
 *   - les heures d'ouverture,
 *   - le compte administrateur "admin",
 *   - les traders de demo alice, bob et carol (mot de passe "password123").
 * S'execute en premier (Order 0), avant le rechargement du carnet et l'amorcage.
 */
@Component
@Order(0)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    static final String DEMO_PASSWORD = "password123";
    private static final List<String[]> DEMO_TRADERS = List.of(
        new String[]{"alice", "Alice Martin"},
        new String[]{"bob", "Bob Durand"},
        new String[]{"carol", "Carol Petit"}
    );

    private final ExchangeProperties properties;
    private final SymbolRepository symbolRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MarketHoursService marketHours;
    private final TradingService tradingService;

    public DataInitializer(ExchangeProperties properties, SymbolRepository symbolRepository,
                           UserRepository userRepository, PasswordEncoder passwordEncoder,
                           MarketHoursService marketHours, TradingService tradingService) {
        this.properties = properties;
        this.symbolRepository = symbolRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.marketHours = marketHours;
        this.tradingService = tradingService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (symbolRepository.count() == 0) {
            for (ExchangeProperties.ListedSymbol listed : properties.symbols()) {
                symbolRepository.save(new SymbolEntity(listed.symbol(), listed.name(), listed.referencePrice()));
            }
            log.info("{} symbole(s) cote(s) depuis la configuration", properties.symbols().size());
        }

        marketHours.initialize();

        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(new UserEntity("admin", passwordEncoder.encode(properties.security().adminPassword()),
                "Administrateur", null, Role.ADMIN));
            log.info("Compte administrateur cree: admin");
        }

        if (properties.seedDemoUsers()) {
            for (String[] demo : DEMO_TRADERS) {
                if (!userRepository.existsByUsername(demo[0])) {
                    userRepository.save(new UserEntity(demo[0], passwordEncoder.encode(DEMO_PASSWORD), demo[1],
                        demo[0] + "@demo.local", Role.TRADER));
                    tradingService.openAccount(demo[0]);
                }
            }
        }
    }
}
