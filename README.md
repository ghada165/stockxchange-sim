# StockXchange Sim — Moteur de matching d'ordres boursiers

Plateforme de bourse simulee avec moteur de matching temps reel
(price-time priority), API REST Spring Boot, persistance JPA, et
diffusion WebSocket du carnet d'ordres.

## Stack
- Java 21, Spring Boot 3
- Spring Data JPA, PostgreSQL (H2 en dev)
- Spring WebSocket (STOMP)
- Flyway (migrations)
- JUnit 5 + AssertJ (tests unitaires et de concurrence)

## Lancer le projet
```bash
mvn spring-boot:run
```
Par defaut: H2 en memoire, aucun setup necessaire.
Pour PostgreSQL: `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`

## Lancer les tests
```bash
mvn test
```

## Structure
```
src/main/java/com/exchange/matching/
├── model/        Order, Trade, enums (domaine pur, sans dependance framework)
├── engine/       OrderBook, MatchingEngine (coeur algorithmique)
├── persistence/  Entites JPA + repositories
├── service/      TradingService (orchestration metier)
├── web/          Controllers REST + DTOs
├── config/       WebSocket, beans Spring
└── exception/    Exceptions metier
```

## Documentation
Voir `/docs` pour le cahier des charges (modules, acteurs, workflows).
