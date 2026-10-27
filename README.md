# StockXchange Sim — Moteur de matching d'ordres boursiers

Plateforme de bourse simulée avec moteur de matching temps réel
(priorité prix puis temps), API REST Spring Boot, persistance JPA et
diffusion WebSocket du carnet d'ordres, avec un front Next.js.

## Stack
- **Backend** : Java 21, Spring Boot 3.3 (Web, Data JPA, Validation, WebSocket STOMP)
- **Base** : H2 en mémoire par défaut, PostgreSQL en option ; schéma géré par Flyway
- **Front** : Next.js 16, React 19, Tailwind CSS 4, STOMP sur SockJS
- **Tests** : JUnit 5 + AssertJ (tests unitaires et de concurrence du moteur)

## Lancer le projet

### Backend
```bash
mvn spring-boot:run
```
Par défaut : H2 en mémoire, aucun setup nécessaire. Au démarrage, un
teneur de marché (`market-maker`) remplit le carnet de chaque symbole
pour qu'on puisse trader tout de suite.

Pour PostgreSQL : `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`

### Front
```bash
cd stockxchange-front
npm install
npm run dev
```
Puis ouvrir http://localhost:3000. Changer de trader (alice, bob, carol…)
en haut à droite pour passer des ordres les uns contre les autres.

### Tests
```bash
mvn test
```

## Fonctionnalités
- Ordres **LIMIT** et **MARKET**, achat et vente, sur AAPL, TSLA, MSFT, GOOGL
- Matching **priorité prix puis temps**, exécution au prix de l'ordre déjà dans le carnet
- **Ordre MARKET protégé** : prix plafonné au pire niveau du carnet au moment
  de l'envoi, pour qu'un acheteur ne dépense jamais plus que ce qui a été bloqué
- **Anti-auto-trade** : un utilisateur ne peut pas exécuter contre son propre ordre
- **Réservation** des fonds (achat) ou des actions (vente) avant le matching
- **Annulation** d'un ordre actif, avec libération exacte de la réservation
- Compte de démo à la première connexion : 100 000 $ et 100 actions de chaque symbole
- Rechargement du carnet au redémarrage (profil PostgreSQL)
- Diffusion **temps réel** du carnet et des transactions (WebSocket)

## API REST

| Méthode | URL | Rôle |
|---|---|---|
| POST | `/api/orders` | Passer un ordre (en-tête `X-User-Id`) |
| DELETE | `/api/orders/{id}` | Annuler un ordre actif |
| GET | `/api/orders/my` | Historique de mes ordres |
| GET | `/api/wallet` | Solde, solde bloqué, positions |
| GET | `/api/market/symbols` | Symboles cotés, dernier prix, meilleurs prix |
| GET | `/api/market/{symbol}/orderbook` | Carnet d'ordres agrégé par prix |
| GET | `/api/market/{symbol}/trades` | 50 dernières transactions |

WebSocket (STOMP sur `/ws`) : `/topic/market/{symbol}/orderbook` et
`/topic/market/{symbol}/trades`.

## Notions Java 21 utilisées
| Notion | Où |
|---|---|
| **Records** | Tous les DTO (`dto/`), `Trade`, `MatchResult`, `ExchangeProperties`, événements `MarketEvent` |
| **Record patterns** | `MarketBroadcaster.publish` (switch qui déconstruit les événements), `TradingService.validateRequest` (switch avec gardes `when`) |
| **Sealed interface** | `MarketEvent permits OrderBookUpdated, TradeExecuted` : switch exhaustif sans `default` |
| **Virtual threads** | `spring.threads.virtual.enabled: true` (une requête = un virtual thread) ; test de concurrence avec `newVirtualThreadPerTaskExecutor()` ; verrous `ReentrantLock` plutôt que `synchronized` |

## Structure
```
src/main/java/com/exchange/matching/
├── model/        Order, Trade (record), enums — domaine pur, sans framework
├── engine/       OrderBook, MatchingEngine, MatchResult — cœur algorithmique
├── service/      TradingService — orchestration métier (réservation, règlement)
├── controller/   OrderController, MarketController, WalletController, GlobalExceptionHandler
├── dto/          Records d'entrée/sortie de l'API
├── event/        MarketEvent (sealed), MarketBroadcaster (WebSocket)
├── persistence/  Entités JPA + repositories
├── config/       Beans, CORS, WebSocket, chargement du carnet, teneur de marché, propriétés
└── exception/    Exceptions métier
stockxchange-front/   Interface Next.js
```

## Choix techniques et limites
- **Concurrence** : un `ReentrantLock` par symbole dans le moteur ; deux
  symboles différents se traitent en parallèle.
- **Pas d'interblocage en base** : le règlement d'un trade verrouille tous
  les comptes concernés dans un ordre global fixe (wallets triés par
  utilisateur, puis positions, puis ordres).
- **Limite assumée** : le matching se fait en mémoire, hors transaction. Si
  le règlement en base échouait après un match, mémoire et base
  divergeraient. En production, on utiliserait un journal d'événements
  (event sourcing / outbox pattern).
- **Authentification** : l'utilisateur est identifié par l'en-tête
  `X-User-Id` en attendant Spring Security + JWT. À ne pas utiliser tel quel
  en production.
- Prix au centime et quantités en actions entières : aucun arrondi sur les
  montants bloqués.
