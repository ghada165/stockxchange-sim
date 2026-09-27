"use client";

import { useCallback, useEffect, useState } from "react";
import { TopBar } from "@/components/TopBar";
import { OrderBookPanel } from "@/components/OrderBookPanel";
import { OrderForm } from "@/components/OrderForm";
import { WalletPanel } from "@/components/WalletPanel";
import { TradeFeed } from "@/components/TradeFeed";
import { api, WalletResponse } from "@/lib/api";
import { useMarketSocket } from "@/lib/useMarketSocket";

export default function Home() {
  const [userId, setUserId] = useState("alice");
  const [symbol, setSymbol] = useState("AAPL");
  const [wallet, setWallet] = useState<WalletResponse | null>(null);
  const [toast, setToast] = useState<{ message: string; isError: boolean } | null>(null);

  const { connected, orderBook, trades } = useMarketSocket(symbol);

  const refreshWallet = useCallback(() => {
    api.wallet(userId).then(setWallet).catch(() => setWallet(null));
  }, [userId]);

  useEffect(() => {
    refreshWallet();
  }, [refreshWallet]);

  useEffect(() => {
    if (trades.length > 0) refreshWallet();
  }, [trades, refreshWallet]);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  function handlePlaced(message: string, isError = false) {
    setToast({ message, isError });
    if (!isError) refreshWallet();
  }

  return (
    <div className="min-h-screen">
      <TopBar
        symbol={symbol}
        onSymbolChange={setSymbol}
        userId={userId}
        onUserIdChange={setUserId}
        connected={connected}
      />

      <main className="grid grid-cols-1 gap-4 p-6 lg:grid-cols-[2fr_1fr]">
        <div className="flex flex-col gap-4">
          <OrderBookPanel book={orderBook} symbol={symbol} />
          <TradeFeed trades={trades} />
        </div>

        <div className="flex flex-col gap-4">
          <OrderForm userId={userId} symbol={symbol} onPlaced={handlePlaced} />
          <WalletPanel wallet={wallet} />
        </div>
      </main>

      {toast && (
        <div
          role="status"
          className={`fixed bottom-6 right-6 border px-4 py-3 text-sm ${
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
