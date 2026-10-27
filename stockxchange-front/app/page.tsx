"use client";

import { useCallback, useEffect, useState } from "react";
import { TopBar } from "@/components/TopBar";
import { MarketHeader } from "@/components/MarketHeader";
import { OrderBookPanel } from "@/components/OrderBookPanel";
import { OrderForm } from "@/components/OrderForm";
import { WalletPanel } from "@/components/WalletPanel";
import { TradeFeed } from "@/components/TradeFeed";
import { MyOrdersPanel } from "@/components/MyOrdersPanel";
import { api, MarketSummary, OrderResponse, WalletResponse } from "@/lib/api";
import { useMarketSocket } from "@/lib/useMarketSocket";

export default function Home() {
  const [userId, setUserId] = useState("alice");
  const [symbol, setSymbol] = useState("AAPL");
  const [symbols, setSymbols] = useState<MarketSummary[]>([]);
  // Compte memorise avec son proprietaire: apres un changement d'utilisateur,
  // on n'affiche jamais le portefeuille du precedent.
  const [account, setAccount] = useState<{ owner: string; wallet: WalletResponse | null; orders: OrderResponse[] }>({
    owner: "",
    wallet: null,
    orders: [],
  });
  const [pickedPrice, setPickedPrice] = useState<{ price: number; nonce: number } | null>(null);
  const [toast, setToast] = useState<{ message: string; isError: boolean } | null>(null);
  const [backendDown, setBackendDown] = useState(false);

  const { connected, orderBook, trades, version } = useMarketSocket(symbol);

  const refreshAccount = useCallback(() => {
    Promise.all([api.wallet(userId), api.myOrders(userId)])
      .then(([wallet, orders]) => setAccount({ owner: userId, wallet, orders }))
      .catch(() => setAccount({ owner: userId, wallet: null, orders: [] }));
  }, [userId]);

  const wallet = account.owner === userId ? account.wallet : null;
  const orders = account.owner === userId ? account.orders : [];

  const refreshSymbols = useCallback(() => {
    api
      .symbols()
      .then((list) => {
        setSymbols(list);
        setBackendDown(false);
      })
      .catch(() => setBackendDown(true));
  }, []);

  // Changement d'utilisateur: on recharge son compte.
  useEffect(() => {
    refreshAccount();
  }, [refreshAccount]);

  // Chaque evenement de marche peut toucher nos ordres (execution par un autre trader).
  useEffect(() => {
    refreshSymbols();
    if (version > 0) refreshAccount();
  }, [version, refreshSymbols, refreshAccount]);

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
      await api.cancelOrder(userId, order.id);
      notify(`Ordre ${order.symbol} annulé`);
    } catch (err) {
      notify(err instanceof Error ? err.message : "Annulation impossible", true);
    }
  }

  return (
    <div className="min-h-screen">
      <TopBar
        symbols={symbols}
        symbol={symbol}
        onSymbolChange={setSymbol}
        userId={userId}
        onUserIdChange={setUserId}
        connected={connected}
      />

      {backendDown && (
        <p role="alert" className="border-b border-ask-500/40 bg-ask-500/10 px-6 py-2 text-sm text-ask-400">
          Backend injoignable. Lancez <code className="num">mvn spring-boot:run</code> puis rechargez la page.
        </p>
      )}

      {/* Mobile: marche, puis formulaire, puis mes ordres. Grand ecran: 2 colonnes. */}
      <main className="grid grid-cols-1 items-start gap-4 p-4 sm:p-6 xl:grid-cols-[minmax(0,1fr)_22rem] xl:grid-rows-[auto_1fr]">
        <div className="flex min-w-0 flex-col gap-4 xl:col-start-1 xl:row-start-1">
          <MarketHeader
            symbol={symbol}
            summary={symbols.find((s) => s.symbol === symbol)}
            book={orderBook}
            trades={trades}
          />
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
          <OrderForm
            userId={userId}
            symbol={symbol}
            book={orderBook}
            wallet={wallet}
            pickedPrice={pickedPrice}
            onPlaced={notify}
          />
          <WalletPanel wallet={wallet} userId={userId} prices={symbols} />
        </div>

        <div className="min-w-0 xl:col-start-1 xl:row-start-2">
          <MyOrdersPanel orders={orders} onCancel={cancelOrder} />
        </div>
      </main>

      {toast && (
        <div
          role="status"
          className={`fixed bottom-6 right-6 max-w-sm border px-4 py-3 text-sm ${
            toast.isError
              ? "border-ask-500 bg-ink-900 text-ask-400"
              : "border-bid-500 bg-ink-900 text-bid-400"
          }`}
        >
          {toast.message}
        </div>
      )}
    </div>
  );
}
