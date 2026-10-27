"use client";

import { useCallback, useEffect, useState } from "react";
import { AppHeader } from "@/components/AppHeader";
import { SymbolTabs } from "@/components/SymbolTabs";
import { MarketHeader } from "@/components/MarketHeader";
import { PriceChart, CandleInterval } from "@/components/PriceChart";
import { OrderBookPanel } from "@/components/OrderBookPanel";
import { OrderForm } from "@/components/OrderForm";
import { WalletPanel } from "@/components/WalletPanel";
import { TradeFeed } from "@/components/TradeFeed";
import { MyOrdersPanel } from "@/components/MyOrdersPanel";
import { VisitorPanel } from "@/components/VisitorPanel";
import {
  api,
  Candle,
  MarketStatus,
  MarketSummary,
  OrderResponse,
  SymbolStats,
  WalletResponse,
} from "@/lib/api";
import { DAY_LABELS, formatClock } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useMarketSocket } from "@/lib/useMarketSocket";

interface Account {
  owner: string;
  wallet: WalletResponse | null;
  orders: OrderResponse[];
}

export default function MarketPage() {
  const session = useSession();
  const username = session?.user.username ?? null;
  const isTrader = session?.user.role === "TRADER";

  const [symbol, setSymbol] = useState("AAPL");
  const [symbols, setSymbols] = useState<MarketSummary[]>([]);
  const [marketStatus, setMarketStatus] = useState<MarketStatus | null>(null);
  const [interval, setChartInterval] = useState<CandleInterval>("1m");
  const [chart, setChart] = useState<{ key: string; candles: Candle[] }>({ key: "", candles: [] });
  const [stats, setStats] = useState<SymbolStats | null>(null);
  // Compte memorise avec son proprietaire: apres un changement d'utilisateur,
  // on n'affiche jamais le portefeuille du precedent.
  const [account, setAccount] = useState<Account>({ owner: "", wallet: null, orders: [] });
  const [pickedPrice, setPickedPrice] = useState<{ price: number; nonce: number } | null>(null);
  const [toast, setToast] = useState<{ message: string; isError: boolean } | null>(null);
  const [backendDown, setBackendDown] = useState(false);

  const { connected, orderBook, trades, version } = useMarketSocket(symbol);

  const refreshAccount = useCallback(() => {
    if (!username || !isTrader) return;
    Promise.all([api.wallet(), api.myOrders()])
      .then(([wallet, orders]) => setAccount({ owner: username, wallet, orders }))
      .catch(() => setAccount({ owner: username, wallet: null, orders: [] }));
  }, [username, isTrader]);

  const refreshMarket = useCallback(() => {
    Promise.all([api.symbols(), api.marketStatus()])
      .then(([list, status]) => {
        setSymbols(list);
        setMarketStatus(status);
        setBackendDown(false);
      })
      .catch(() => setBackendDown(true));
  }, []);

  useEffect(() => {
    refreshAccount();
  }, [refreshAccount]);

  // Chaque evenement de marche peut toucher nos ordres (execution par un autre trader),
  // les prix, les statistiques et la courbe.
  useEffect(() => {
    refreshMarket();
    if (version > 0) refreshAccount();
    const key = `${symbol}/${interval}`;
    api.candles(symbol, interval).then((candles) => setChart({ key, candles })).catch(() => {});
    api.stats(symbol).then(setStats).catch(() => setStats(null));
  }, [version, symbol, interval, refreshMarket, refreshAccount]);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4500);
    return () => clearTimeout(timer);
  }, [toast]);

  function notify(message: string, isError = false) {
    setToast({ message, isError });
    refreshAccount();
  }

  async function cancelOrder(order: OrderResponse) {
    try {
      await api.cancelOrder(order.id);
      notify(`Ordre ${order.symbol} annulé`);
    } catch (err) {
      notify(err instanceof Error ? err.message : "Annulation impossible", true);
    }
  }

  const summary = symbols.find((s) => s.symbol === symbol);
  const wallet = account.owner === username ? account.wallet : null;
  const orders = account.owner === username ? account.orders : [];
  const candles = chart.key === `${symbol}/${interval}` ? chart.candles : [];

  let disabledReason: string | null = null;
  if (summary?.status === "SUSPENDED") disabledReason = `La cotation de ${symbol} est suspendue par l'administrateur.`;
  else if (marketStatus && !marketStatus.open)
    disabledReason = `Marché fermé. Ouverture ${formatClock(marketStatus.openTime)}–${formatClock(marketStatus.closeTime)} (${marketStatus.tradingDays
      .map((d) => DAY_LABELS[d])
      .join(", ")}).`;

  return (
    <div className="min-h-screen">
      <AppHeader connected={connected} />
      <SymbolTabs symbols={symbols} symbol={symbol} onSymbolChange={setSymbol} />

      {backendDown && (
        <p role="alert" className="border-b border-ask-500/40 bg-ask-500/10 px-6 py-2 text-sm text-ask-400">
          Backend injoignable. Lancez <code className="num">mvn spring-boot:run</code> puis rechargez la page.
        </p>
      )}
      {marketStatus && !marketStatus.open && (
        <p role="status" className="border-b border-gold-500/40 bg-gold-500/10 px-6 py-2 text-sm text-gold-400">
          Marché fermé — les ordres sont refusés jusqu&apos;à la prochaine ouverture ({formatClock(marketStatus.openTime)},{" "}
          {marketStatus.zone}).
        </p>
      )}

      {/* Mobile: marche, puis formulaire, puis mes ordres. Grand ecran: 2 colonnes. */}
      <main className="grid grid-cols-1 items-start gap-4 p-4 sm:p-6 xl:grid-cols-[minmax(0,1fr)_22rem] xl:grid-rows-[auto_1fr]">
        <div className="flex min-w-0 flex-col gap-4 xl:col-start-1 xl:row-start-1">
          <MarketHeader symbol={symbol} summary={summary} book={orderBook} trades={trades} stats={stats} />
          <PriceChart symbol={symbol} candles={candles} interval={interval} onIntervalChange={setChartInterval} />
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <OrderBookPanel
              book={orderBook}
              symbol={symbol}
              onPickPrice={(price) => setPickedPrice({ price, nonce: Date.now() })}
            />
            <TradeFeed trades={trades} />
          </div>
        </div>

        <div className="flex flex-col gap-4 xl:col-start-2 xl:row-span-2 xl:row-start-1">
          {isTrader && session ? (
            <>
              <OrderForm
                symbol={symbol}
                disabledReason={disabledReason}
                book={orderBook}
                wallet={wallet}
                pickedPrice={pickedPrice}
                onPlaced={notify}
              />
              <WalletPanel
                wallet={wallet}
                owner={session.user.displayName ?? session.user.username}
                prices={symbols}
              />
            </>
          ) : (
            <VisitorPanel />
          )}
        </div>

        {isTrader && (
          <div className="min-w-0 xl:col-start-1 xl:row-start-2">
            <MyOrdersPanel orders={orders} onCancel={cancelOrder} />
          </div>
        )}
      </main>

      {toast && (
        <div
          role="status"
          className={`fixed bottom-6 right-6 max-w-sm border px-4 py-3 text-sm ${
            toast.isError ? "border-ask-500 bg-ink-900 text-ask-400" : "border-bid-500 bg-ink-900 text-bid-400"
          }`}
        >
          {toast.message}
        </div>
      )}
    </div>
  );
}
