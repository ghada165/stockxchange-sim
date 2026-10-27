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
Par défaut : H2 en mémoire, aucun setup nécessaire. Au premier démarrage, les
symboles, les heures de marché et les comptes de démonstration sont créés, et
un teneur de marché (`market-maker`) remplit le carnet de chaque symbole.

Pour PostgreSQL : `mvn spring-boot:run -Dspring-boot.run.profiles=postgres`

### Front
```bash
cd stockxchange-front
npm install
npm run dev
```
Puis ouvrir http://localhost:3000.

### Comptes de démonstration
| Utilisateur | Mot de passe | Rôle |
|---|---|---|
| `alice`, `bob`, `carol` | `password123` | Trader |
| `admin` | `admin123` | Administrateur |

Sans connexion, on navigue en **visiteur** (consultation du marché seulement).

### Tests
```bash
mvn test
```
- Tests unitaires du moteur, de concurrence, des heures de marché et des chandeliers
- `ApiIntegrationTest` : démarre toute l'application et joue les workflows du
  cahier des charges par HTTP (visiteur, inscription, ordre, historique,
  statistiques, droits admin, suspension, nouveau symbole, marché fermé)

## Couverture du cahier des charges

| Module | Fonctionnalités | Où |
|---|---|---|
| Auth & Utilisateurs | Inscription / connexion **JWT**, profil, rôles Trader / Admin | `AuthController`, `AuthService`, `TokenService`, `SecurityConfig` |
| Marché | Symboles en base, prix courant, **historique de prix** (chandeliers 1m/5m/15m/1h), **heures d'ouverture** | `MarketController`, `SymbolService`, `MarketHoursService`, `ReportingService` |
| Ordres | LIMIT / MARKET, annulation, ordres en cours et historique | `OrderController`, `TradingService` |
| Matching Engine | Priorité prix puis temps, exécution totale ou partielle, un carnet par symbole | `engine/` |
| Wallet & Positions | Solde disponible / bloqué, blocage des fonds (BUY) et des actions (SELL), règlement | `TradingService`, `WalletController` |
| Notification temps réel | Carnet et trades en direct (WebSocket STOMP) | `event/MarketBroadcaster` |
| Historique & Reporting | Historique par utilisateur (relevé + export CSV), par symbole, **statistiques** (volume, VWAP, plus haut/bas) | `HistoryController`, `ReportingService` |
| Administration | Ajouter / suspendre un symbole, superviser / suspendre les utilisateurs, **journal d'activité global**, heures de marché | `AdminController`, `AdminService`, `ActivityLogService` |

| Acteur | Droits (vérifiés par `SecurityConfig`) |
|---|---|
| Visiteur | `GET /api/market/**`, inscription, connexion |
| Trader | + `/api/orders/**`, `/api/wallet/**`, `/api/history/**` |
| Admin | `/api/admin/**` (ne passe pas d'ordres) |
| Système | Le moteur apparie, règle et diffuse automatiquement dans `TradingService` |

## Fonctionnalités du moteur
- **Ordre MARKET protégé** : prix plafonné au pire niveau du carnet au moment
  de l'envoi, pour qu'un acheteur ne dépense jamais plus que ce qui a été bloqué
- **Anti-auto-trade** : un utilisateur ne peut pas exécuter contre son propre ordre
- **Réservation** des fonds ou des actions avant le matching, libération exacte à l'annulation
- Rechargement du carnet au redémarrage (profil PostgreSQL)

## API REST

| Méthode | URL | Accès | Rôle |
|---|---|---|---|
| POST | `/api/auth/register` | public | Inscription d'un trader, renvoie un jeton |
| POST | `/api/auth/login` | public | Connexion, renvoie un jeton |
| GET / PUT | `/api/auth/me` | connecté | Profil |
| GET | `/api/market/status` | public | Marché ouvert ou fermé, horaires |
| GET | `/api/market/symbols` | public | Symboles, statut, dernier prix, meilleurs prix |
| GET | `/api/market/{symbol}/orderbook` | public | Carnet d'ordres |
| GET | `/api/market/{symbol}/trades` | public | 50 dernières transactions |
| GET | `/api/market/{symbol}/candles?interval=1m` | public | Historique de prix |
| GET | `/api/market/{symbol}/stats` | public | Statistiques 24 h |
| POST | `/api/orders` | trader | Passer un ordre |
| DELETE | `/api/orders/{id}` | trader | Annuler un ordre actif |
| GET | `/api/orders/my` | trader | Mes ordres |
| GET | `/api/wallet` | trader | Solde et positions |
| GET | `/api/history/trades` | trader | Relevé de mes exécutions |
| GET | `/api/admin/overview` | admin | Tableau de bord |
| GET | `/api/admin/users` | admin | Utilisateurs |
| PATCH | `/api/admin/users/{username}/status` | admin | Suspendre / réactiver |
| POST | `/api/admin/symbols` | admin | Ajouter un symbole |
| PATCH | `/api/admin/symbols/{symbol}/status` | admin | Suspendre / réactiver un symbole |
| PUT | `/api/admin/market-hours` | admin | Heures d'ouverture |
| GET | `/api/admin/logs?actor=&action=` | admin | Journal d'activité |

Authentification : en-tête `Authorization: Bearer <jeton>` (jeton renvoyé par login ou register).
WebSocket (STOMP sur `/ws`, public) : `/topic/market/{symbol}/orderbook` et `/topic/market/{symbol}/trades`.

## Notions Java 21 utilisées
| Notion | Où |
|---|---|
| **Records** | Tous les DTO (`dto/`), `Trade`, `MatchResult`, `MarketHours` (record avec comportement), `TradeStats` (projection JPQL `select new`), `ExchangeProperties` et ses records imbriqués, événements `MarketEvent` |
| **Record patterns** | `MarketBroadcaster.publish` (switch qui déconstruit les événements), `TradingService.validateRequest` (switch avec gardes `when`) |
| **Sealed interface** | `MarketEvent permits OrderBookUpdated, TradeExecuted` : switch exhaustif sans `default` |
| **Virtual threads** | `spring.threads.virtual.enabled: true` (une requête = un virtual thread) ; test de concurrence avec `newVirtualThreadPerTaskExecutor()` ; verrous `ReentrantLock` plutôt que `synchronized` |

## Structure
```
src/main/java/com/exchange/matching/
├── model/        Order, Trade (record), enums — domaine pur, sans framework
├── engine/       OrderBook, MatchingEngine, MatchResult — cœur algorithmique
├── service/      TradingService (réservation, règlement), AuthService, TokenService,
│                 SymbolService, MarketHoursService, ReportingService, AdminService, ActivityLogService
├── reporting/    CandleAggregator, TradeStats — calculs de reporting purs
├── controller/   Auth, Order, Market, Wallet, History, Admin + GlobalExceptionHandler
├── dto/          Records d'entrée/sortie de l'API
├── event/        MarketEvent (sealed), MarketBroadcaster (WebSocket)
├── persistence/  Entités JPA + repositories
├── config/       Sécurité JWT + CORS, WebSocket, données initiales, chargement du carnet, teneur de marché, propriétés
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
- **Authentification** : JWT signé en HMAC-SHA256, sans session serveur.
  Changer `exchange.security.jwt-secret` et le mot de passe admin en production
  (variables `EXCHANGE_SECURITY_JWT_SECRET` et `EXCHANGE_SECURITY_ADMIN_PASSWORD`).
- Prix au centime et quantités en actions entières : aucun arrondi sur les
  montants bloqués.
