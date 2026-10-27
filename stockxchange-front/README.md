# StockXchange Sim — Front

Interface de trading en Next.js pour le moteur de matching (voir le README à la racine).

## Lancer
```bash
npm install
npm run dev
```
Ouvrir http://localhost:3000. Le backend Spring doit tourner sur http://localhost:8080
(modifiable avec `NEXT_PUBLIC_API_BASE_URL` dans `.env.local`, voir `.env.local.example`).

## Pages
| Page | Accès | Contenu |
|---|---|---|
| `/` Marché | tous | Symboles, statistiques 24 h, historique de prix, carnet, transactions. Trader : formulaire d'ordre, portefeuille, mes ordres |
| `/login` | public | Connexion, inscription, comptes de démonstration |
| `/historique` | trader | Relevé des exécutions, synthèse, filtre par titre, export CSV |
| `/profil` | connecté | Nom affiché et email |
| `/admin` | admin | Tableau de bord, symboles, heures de marché, utilisateurs, journal d'activité |

Le jeton JWT est gardé dans le `localStorage` du navigateur ; s'il expire, l'interface repasse en mode visiteur.

## Structure
```
app/                  Pages (Marché, login, historique, profil, admin)
components/           AppHeader, SymbolTabs, MarketHeader, PriceChart, OrderBookPanel, TradeFeed,
                      OrderForm, MyOrdersPanel, WalletPanel, VisitorPanel, RequireRole
lib/api.ts            Client de l'API REST (jeton Bearer) et types
lib/session.ts        Session (jeton + profil) partagée par toutes les pages
lib/useMarketSocket.ts  Carnet et transactions : chargement REST puis temps réel WebSocket
lib/format.ts         Formatage des prix, quantités et dates (fr-FR)
```
