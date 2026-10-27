# StockXchange Sim — Front

Interface de trading en Next.js pour le moteur de matching (voir le README à la racine).

## Lancer
```bash
npm install
npm run dev
```
Ouvrir http://localhost:3000. Le backend Spring doit tourner sur http://localhost:8080
(modifiable avec `NEXT_PUBLIC_API_BASE_URL` dans `.env.local`, voir `.env.local.example`).

## Écran
- **Bandeau des symboles** : dernier prix et variation depuis le prix de référence
- **Carnet d'ordres** : cliquer sur un niveau reprend son prix dans le formulaire
- **Transactions** en direct (▲ hausse, ▼ baisse)
- **Formulaire d'ordre** : limite ou marché, total estimé, contrôle du solde et des actions disponibles
- **Mes ordres** : ordres actifs avec bouton d'annulation, et historique
- **Portefeuille** : cash disponible et bloqué, positions et leur valeur

## Structure
```
app/page.tsx          Page principale (état, rafraîchissements)
components/           TopBar, MarketHeader, OrderBookPanel, TradeFeed, OrderForm, MyOrdersPanel, WalletPanel
lib/api.ts            Client de l'API REST et types
lib/useMarketSocket.ts  Carnet et transactions : chargement REST puis temps réel WebSocket
lib/format.ts         Formatage des prix et quantités (fr-FR)
```
